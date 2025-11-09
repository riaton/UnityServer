package com.game.matching.service;

import com.game.matching.dto.CheckUserStateResponse;
import com.game.matching.dto.ListJoiningPartyUsersResponse;
import com.game.matching.dto.StartGameResponse;
import com.game.matching.exception.BusinessException;
import com.game.matching.model.Teamspace;
import com.game.matching.repository.RedisRepository;
import com.game.matching.websocket.GameWebSocketHandler;
import com.game.matching.websocket.WebSocketConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;

@Service
public class GameService {
    
    private static final Logger logger = LoggerFactory.getLogger(GameService.class);
    
    private final RedisRepository redisRepository;
    private final WebSocketConnectionManager connectionManager;
    private final GameWebSocketHandler webSocketHandler;
    private final LoggingService loggingService;
    
    public GameService(RedisRepository redisRepository,
                      WebSocketConnectionManager connectionManager,
                      GameWebSocketHandler webSocketHandler,
                      LoggingService loggingService) {
        this.redisRepository = redisRepository;
        this.connectionManager = connectionManager;
        this.webSocketHandler = webSocketHandler;
        this.loggingService = loggingService;
    }
    
    /**
     * API④: ゲーム開始
     */
    public StartGameResponse startGame(String userId, String teamspaceId) {
        try {
            // teamspaceIdが存在するかチェック
            Optional<Teamspace> teamspaceOpt = redisRepository.getTeamspace(teamspaceId);
            if (teamspaceOpt.isEmpty()) {
                throw new BusinessException("TEAMSPACE_NOT_FOUND",
                                          "指定されたteamspaceIdが存在しません",
                                          HttpStatus.NOT_FOUND);
            }
            
            Teamspace teamspace = teamspaceOpt.get();
            
            // ユーザーがteamspaceの主催者かどうかチェック
            if (!teamspace.isOrganizer(userId)) {
                throw new BusinessException("NOT_A_AUTHOR",
                                          "ユーザーはこのteamspaceの主催者ではありません",
                                          HttpStatus.CONFLICT);
            }
            
            // ユーザーが他のチームの主催者/メンバーでないかチェック（既にチェック済みのはずだが念のため）
            Optional<Teamspace> existingAsOrganizer = redisRepository.findTeamspaceByOrganizer(userId);
            Optional<Teamspace> existingAsMember = redisRepository.findTeamspaceByMember(userId);
            
            if ((existingAsOrganizer.isPresent() && 
                 !existingAsOrganizer.get().getTeamspaceId().equals(teamspaceId)) ||
                (existingAsMember.isPresent() && 
                 !existingAsMember.get().getTeamspaceId().equals(teamspaceId))) {
                throw new BusinessException("USER_ALREADY_IN_TEAM",
                                          "ユーザーは既に他のチームに参加/主催中です",
                                          HttpStatus.CONFLICT);
            }
            
            // UUID4でpartyIdを生成
            String partyId = UUID.randomUUID().toString();
            teamspace.setPartyId(partyId);
            redisRepository.saveTeamspace(teamspace);
            
            // 主催者を除く参加者全員へWebSocket通知
            List<String> notifiedMembers = new ArrayList<>();
            List<String> failedMembers = new ArrayList<>();
            
            Set<WebSocketSession> sessions = connectionManager.getConnections(teamspaceId);
            Map<String, Object> notification = Map.of("type", "partyId", "partyId", partyId);
            
            for (WebSocketSession session : sessions) {
                // 主催者を除外（主催者はHTTPレスポンスでpartyIdを取得）
                String sessionUserId = extractUserIdFromSession(session);
                if (sessionUserId != null && !sessionUserId.equals(userId)) {
                    if (webSocketHandler.sendMessage(session, notification)) {
                        notifiedMembers.add(sessionUserId);
                    } else {
                        failedMembers.add(sessionUserId);
                    }
                }
            }
            
            // ログ出力（通知成功/失敗したメンバーを含む）
            loggingService.logGameStart(userId, teamspaceId, partyId, 
                                       notifiedMembers, failedMembers);
            
            return new StartGameResponse(partyId);
            
        } catch (BusinessException e) {
            loggingService.logGameStartFailed(userId, e.getErrorCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Failed to start game", e);
            loggingService.logGameStartFailed(userId, "INTERNAL_SERVER_ERROR", e.getMessage());
            throw new BusinessException("INTERNAL_SERVER_ERROR", "サーバーエラー", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * API⑥: ユーザー状態確認
     */
    public CheckUserStateResponse checkUserState(String userId) {
        // userIdのバリデーション
        if (userId == null || userId.trim().isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR",
                                       "userId is required and cannot be empty",
                                       HttpStatus.BAD_REQUEST);
        }
        if (userId.length() > 50) {
            throw new BusinessException("VALIDATION_ERROR",
                                       "userId must be 50 characters or less",
                                       HttpStatus.BAD_REQUEST);
        }
        
        try {
            // すべてのteamspaceを取得
            List<Teamspace> allTeamspaces = redisRepository.getAllTeamspaces();
            
            // 優先順位: 主催者 > ゲーム中 > 参加中
            Teamspace organizingTeamspace = null;
            Teamspace playingTeamspace = null;
            Teamspace joiningTeamspace = null;
            
            for (Teamspace teamspace : allTeamspaces) {
                // 「ゲーム未スタートかつ主催者」
                if (teamspace.isOrganizer(userId) && teamspace.getPartyId() == null) {
                    organizingTeamspace = teamspace;
                    break; // 主催者が最優先
                }
                // 「すでに他のパーティでゲームを開始している」
                if (teamspace.getMembers().contains(userId) && teamspace.getPartyId() != null) {
                    if (playingTeamspace == null) {
                        playingTeamspace = teamspace;
                    }
                }
                // 「ゲーム未スタートかつ他のteamspaceに参加中」
                if (teamspace.getMembers().contains(userId) && 
                    !teamspace.isOrganizer(userId) && 
                    teamspace.getPartyId() == null) {
                    if (joiningTeamspace == null) {
                        joiningTeamspace = teamspace;
                    }
                }
            }
            
            CheckUserStateResponse response;
            
            if (organizingTeamspace != null) {
                // 主催者
                response = new CheckUserStateResponse(
                    true, false, false,
                    organizingTeamspace.getTeamspaceId(), ""
                );
            } else if (playingTeamspace != null) {
                // ゲーム中
                response = new CheckUserStateResponse(
                    false, false, true,
                    "", playingTeamspace.getPartyId()
                );
            } else if (joiningTeamspace != null) {
                // 参加中
                response = new CheckUserStateResponse(
                    false, true, false,
                    joiningTeamspace.getTeamspaceId(), ""
                );
            } else {
                // いずれにも該当しない
                response = new CheckUserStateResponse(
                    false, false, false,
                    "", ""
                );
            }
            
            // ログ出力
            loggingService.logUserStateChecked(userId);
            
            return response;
            
        } catch (BusinessException e) {
            loggingService.logUserStateCheckFailed(userId, e.getErrorCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Failed to check user state", e);
            loggingService.logUserStateCheckFailed(userId, "INTERNAL_SERVER_ERROR", e.getMessage());
            throw new BusinessException("INTERNAL_SERVER_ERROR", "サーバーエラー", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * API⑦: 参加者一覧取得
     */
    public ListJoiningPartyUsersResponse listJoiningPartyUsers(String userId, String teamspaceId) {
        // userIdのバリデーション
        if (userId == null || userId.trim().isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR",
                                       "userId is required and cannot be empty",
                                       HttpStatus.BAD_REQUEST);
        }
        if (userId.length() > 50) {
            throw new BusinessException("VALIDATION_ERROR",
                                       "userId must be 50 characters or less",
                                       HttpStatus.BAD_REQUEST);
        }
        
        // teamspaceIdのバリデーション
        if (teamspaceId == null || teamspaceId.trim().isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR",
                                       "teamspaceId is required and cannot be empty",
                                       HttpStatus.BAD_REQUEST);
        }
        // UUID形式チェック
        if (!teamspaceId.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
            throw new BusinessException("VALIDATION_ERROR",
                                       "teamspaceId must be a valid UUID",
                                       HttpStatus.BAD_REQUEST);
        }
        
        try {
            // teamspaceIdが存在するかチェック
            Optional<Teamspace> teamspaceOpt = redisRepository.getTeamspace(teamspaceId);
            if (teamspaceOpt.isEmpty()) {
                throw new BusinessException("TEAMSPACE_NOT_FOUND",
                                          "指定されたteamspaceIdが存在しません",
                                          HttpStatus.NOT_FOUND);
            }
            
            Teamspace teamspace = teamspaceOpt.get();
            List<String> userIds = new ArrayList<>(teamspace.getMembers());
            
            // ログ出力
            loggingService.logUsersListed(userId, teamspaceId, userIds);
            
            return new ListJoiningPartyUsersResponse(userIds);
            
        } catch (BusinessException e) {
            loggingService.logUsersListFailed(userId, teamspaceId, e.getErrorCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Failed to list users", e);
            loggingService.logUsersListFailed(userId, teamspaceId, "INTERNAL_SERVER_ERROR", e.getMessage());
            throw new BusinessException("INTERNAL_SERVER_ERROR", "サーバーエラー", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    private String extractUserIdFromSession(WebSocketSession session) {
        return connectionManager.getUserId(session);
    }
}

