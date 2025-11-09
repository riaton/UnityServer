package com.game.matching.service;

import com.game.matching.dto.OrganizeTeamResponse;
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
public class TeamService {
    
    private static final Logger logger = LoggerFactory.getLogger(TeamService.class);
    private static final int MAX_TEAM_MEMBERS = 4;
    
    private final RedisRepository redisRepository;
    private final WebSocketConnectionManager connectionManager;
    private final GameWebSocketHandler webSocketHandler;
    private final LoggingService loggingService;
    
    public TeamService(RedisRepository redisRepository,
                      WebSocketConnectionManager connectionManager,
                      GameWebSocketHandler webSocketHandler,
                      LoggingService loggingService) {
        this.redisRepository = redisRepository;
        this.connectionManager = connectionManager;
        this.webSocketHandler = webSocketHandler;
        this.loggingService = loggingService;
    }
    
    /**
     * API①: チームスペース作成
     */
    public OrganizeTeamResponse organizeTeam(String userId) {
        try {
            // ユーザーが既に他のチームに参加/主催していないかチェック
            Optional<Teamspace> existingAsOrganizer = redisRepository.findTeamspaceByOrganizer(userId);
            Optional<Teamspace> existingAsMember = redisRepository.findTeamspaceByMember(userId);
            
            if (existingAsOrganizer.isPresent() || existingAsMember.isPresent()) {
                throw new BusinessException("USER_ALREADY_IN_TEAM",
                                          "ユーザーは既に他のチームに参加しています",
                                          HttpStatus.CONFLICT);
            }
            
            // UUID4でteamspaceIdを生成
            String teamspaceId = UUID.randomUUID().toString();
            
            // Redisに保存
            Teamspace teamspace = new Teamspace(teamspaceId, userId);
            redisRepository.saveTeamspace(teamspace);
            
            // ログ出力
            loggingService.logTeamspaceCreated(userId, teamspaceId);
            
            return new OrganizeTeamResponse(teamspaceId);
            
        } catch (BusinessException e) {
            loggingService.logTeamspaceCreateFailed(userId, e.getErrorCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Failed to create teamspace", e);
            loggingService.logTeamspaceCreateFailed(userId, "INTERNAL_SERVER_ERROR", e.getMessage());
            throw new BusinessException("INTERNAL_SERVER_ERROR", "サーバーエラー", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * API②: チーム参加
     */
    public void joinTeam(String userId, String teamspaceId) {
        try {
            // teamspaceIdが存在するかチェック
            Optional<Teamspace> teamspaceOpt = redisRepository.getTeamspace(teamspaceId);
            if (teamspaceOpt.isEmpty()) {
                throw new BusinessException("TEAMSPACE_NOT_FOUND",
                                          "指定されたteamspaceIdが存在しません",
                                          HttpStatus.NOT_FOUND);
            }
            
            Teamspace teamspace = teamspaceOpt.get();
            
            // ユーザーが既に他のチームに参加/主催していないかチェック
            Optional<Teamspace> existingAsOrganizer = redisRepository.findTeamspaceByOrganizer(userId);
            Optional<Teamspace> existingAsMember = redisRepository.findTeamspaceByMember(userId);
            
            if (existingAsOrganizer.isPresent() || 
                (existingAsMember.isPresent() && !existingAsMember.get().getTeamspaceId().equals(teamspaceId))) {
                throw new BusinessException("USER_ALREADY_IN_TEAM",
                                          "ユーザーは既に他のチームに参加しています",
                                          HttpStatus.CONFLICT);
            }
            
            // 既に同じteamspaceのメンバーかチェック
            if (teamspace.isMember(userId)) {
                throw new BusinessException("ALREADY_JOINED",
                                          "ユーザーは既に同じteamspaceに参加中です",
                                          HttpStatus.CONFLICT);
            }
            
            // メンバー数が4人未満かチェック
            if (teamspace.getMembers().size() >= MAX_TEAM_MEMBERS) {
                throw new BusinessException("TEAMSPACE_FULL",
                                          "teamspaceが満員（4人）です",
                                          HttpStatus.CONFLICT);
            }
            
            // メンバーを追加
            teamspace.addMember(userId);
            redisRepository.saveTeamspace(teamspace);
            
            // WebSocket通知
            notifyMemberListUpdate(teamspaceId, teamspace.getMembers());
            
            // ログ出力
            loggingService.logTeamspaceJoined(userId, teamspaceId);
            
        } catch (BusinessException e) {
            loggingService.logTeamspaceJoinFailed(userId, e.getErrorCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Failed to join teamspace", e);
            loggingService.logTeamspaceJoinFailed(userId, "INTERNAL_SERVER_ERROR", e.getMessage());
            throw new BusinessException("INTERNAL_SERVER_ERROR", "サーバーエラー", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * API③: チーム脱退
     */
    public void leaveTeam(String userId, String teamspaceId) {
        try {
            // teamspaceIdが存在するかチェック
            Optional<Teamspace> teamspaceOpt = redisRepository.getTeamspace(teamspaceId);
            if (teamspaceOpt.isEmpty()) {
                throw new BusinessException("TEAMSPACE_NOT_FOUND",
                                          "指定されたteamspaceIdが存在しません",
                                          HttpStatus.NOT_FOUND);
            }
            
            Teamspace teamspace = teamspaceOpt.get();
            
            // ユーザーがteamspaceのメンバーかどうかチェック
            if (!teamspace.isMember(userId)) {
                throw new BusinessException("NOT_A_MEMBER",
                                          "ユーザーはこのteamspaceのメンバーではありません",
                                          HttpStatus.CONFLICT);
            }
            
            // 主催者の場合
            if (teamspace.isOrganizer(userId)) {
                // teamspaceを削除
                redisRepository.deleteTeamspace(teamspaceId);
                loggingService.logTeamspaceLeft(userId, teamspaceId);
                return;
            }
            
            // 参加者の場合
            teamspace.removeMember(userId);
            redisRepository.saveTeamspace(teamspace);
            
            // WebSocket通知
            notifyMemberListUpdate(teamspaceId, teamspace.getMembers());
            
            // ログ出力
            loggingService.logTeamspaceLeft(userId, teamspaceId);
            
        } catch (BusinessException e) {
            loggingService.logTeamspaceLeaveFailed(userId, e.getErrorCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Failed to leave teamspace", e);
            loggingService.logTeamspaceLeaveFailed(userId, "INTERNAL_SERVER_ERROR", e.getMessage());
            throw new BusinessException("INTERNAL_SERVER_ERROR", "サーバーエラー", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * API⑤: 既存パーティ(ゲームプレイ中)への参加
     */
    public void joinExistingParty(String userId, String teamspaceId) {
        try {
            // teamspaceIdが存在するかチェック
            Optional<Teamspace> teamspaceOpt = redisRepository.getTeamspace(teamspaceId);
            if (teamspaceOpt.isEmpty()) {
                throw new BusinessException("TEAMSPACE_NOT_FOUND",
                                          "指定されたteamspaceIdが存在しません",
                                          HttpStatus.NOT_FOUND);
            }
            
            Teamspace teamspace = teamspaceOpt.get();
            
            // ユーザーが既に他のチームに参加/主催していないかチェック
            Optional<Teamspace> existingAsOrganizer = redisRepository.findTeamspaceByOrganizer(userId);
            Optional<Teamspace> existingAsMember = redisRepository.findTeamspaceByMember(userId);
            
            if (existingAsOrganizer.isPresent() || 
                (existingAsMember.isPresent() && !existingAsMember.get().getTeamspaceId().equals(teamspaceId))) {
                throw new BusinessException("USER_ALREADY_IN_TEAM",
                                          "ユーザーは既に他のチームに参加しています",
                                          HttpStatus.CONFLICT);
            }
            
            // 既に同じteamspaceのメンバーかチェック
            if (teamspace.isMember(userId)) {
                throw new BusinessException("ALREADY_JOINED",
                                          "ユーザーは既に同じteamspaceに参加中です",
                                          HttpStatus.CONFLICT);
            }
            
            // メンバー数が4人未満かチェック
            if (teamspace.getMembers().size() >= MAX_TEAM_MEMBERS) {
                throw new BusinessException("TEAMSPACE_FULL",
                                          "該当partyが満員（4人）です",
                                          HttpStatus.CONFLICT);
            }
            
            // partyIdが設定されているかチェック（ゲームが開始されているか）
            if (teamspace.getPartyId() == null || teamspace.getPartyId().isEmpty()) {
                throw new BusinessException("GAME_NOT_STARTED",
                                          "ゲームが開始されていません",
                                          HttpStatus.CONFLICT);
            }
            
            // メンバーを追加
            teamspace.addMember(userId);
            redisRepository.saveTeamspace(teamspace);
            
            // ログ出力
            loggingService.logPartyJoined(userId, teamspaceId);
            
        } catch (BusinessException e) {
            loggingService.logPartyJoinFailed(userId, e.getErrorCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Failed to join existing party", e);
            loggingService.logPartyJoinFailed(userId, "INTERNAL_SERVER_ERROR", e.getMessage());
            throw new BusinessException("INTERNAL_SERVER_ERROR", "サーバーエラー", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * メンバーリスト更新をWebSocketで通知
     */
    private void notifyMemberListUpdate(String teamspaceId, List<String> members) {
        List<String> memberIds = new ArrayList<>(members);
        Map<String, Object> notification = Map.of("type", "memberList", "userIds", memberIds);
        
        Set<WebSocketSession> sessions = connectionManager.getConnections(teamspaceId);
        boolean allSuccess = true;
        
        for (WebSocketSession session : sessions) {
            if (!webSocketHandler.sendMessage(session, notification)) {
                allSuccess = false;
            }
        }
        
        if (!allSuccess) {
            throw new BusinessException("NOTIFICATION_FAILED",
                                      "WebSocket通知に失敗しました",
                                      HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}

