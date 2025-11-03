package com.game.matching.controller;

import com.game.matching.dto.ErrorResponse;
import com.game.matching.dto.StartGameRequest;
import com.game.matching.dto.StartGameResponse;
import com.game.matching.exception.BusinessException;
import com.game.matching.model.Teamspace;
import com.game.matching.repository.RedisRepository;
import com.game.matching.service.LoggingService;
import com.game.matching.websocket.GameWebSocketHandler;
import com.game.matching.websocket.WebSocketConnectionManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;

@RestController
@RequestMapping("/api")
public class GameController {
    
    private static final Logger logger = LoggerFactory.getLogger(GameController.class);
    
    private final RedisRepository redisRepository;
    private final WebSocketConnectionManager connectionManager;
    private final GameWebSocketHandler webSocketHandler;
    private final LoggingService loggingService;
    
    public GameController(RedisRepository redisRepository,
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
    @PostMapping("/start_game")
    public ResponseEntity<StartGameResponse> startGame(
            @Valid @RequestBody StartGameRequest request,
            HttpServletRequest httpRequest) {
        
        String tokenUserId = (String) httpRequest.getAttribute("userId");
        
        // トークンから取得したuserIdとrequest.bodyのuserIdが一致するかチェック
        if (!tokenUserId.equals(request.getUserId())) {
            throw new BusinessException("VALIDATION_ERROR",
                                       "Token userId does not match request userId",
                                       HttpStatus.BAD_REQUEST);
        }
        
        try {
            // teamspaceIdが存在するかチェック
            Optional<Teamspace> teamspaceOpt = redisRepository.getTeamspace(request.getTeamspaceId());
            if (teamspaceOpt.isEmpty()) {
                throw new BusinessException("TEAMSPACE_NOT_FOUND",
                                          "指定されたteamspaceIdが存在しません",
                                          HttpStatus.NOT_FOUND);
            }
            
            Teamspace teamspace = teamspaceOpt.get();
            
            // ユーザーがteamspaceの主催者かどうかチェック
            if (!teamspace.isOrganizer(request.getUserId())) {
                throw new BusinessException("NOT_A_AUTHOR",
                                          "ユーザーはこのteamspaceの主催者ではありません",
                                          HttpStatus.CONFLICT);
            }
            
            // ユーザーが他のチームの主催者/メンバーでないかチェック（既にチェック済みのはずだが念のため）
            Optional<Teamspace> existingAsOrganizer = redisRepository.findTeamspaceByOrganizer(request.getUserId());
            Optional<Teamspace> existingAsMember = redisRepository.findTeamspaceByMember(request.getUserId());
            
            if ((existingAsOrganizer.isPresent() && 
                 !existingAsOrganizer.get().getTeamspaceId().equals(request.getTeamspaceId())) ||
                (existingAsMember.isPresent() && 
                 !existingAsMember.get().getTeamspaceId().equals(request.getTeamspaceId()))) {
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
            
            Set<WebSocketSession> sessions = connectionManager.getConnections(request.getTeamspaceId());
            Map<String, Object> notification = Map.of("type", "partyId", "partyId", partyId);
            
            for (WebSocketSession session : sessions) {
                // 主催者を除外（主催者はHTTPレスポンスでpartyIdを取得）
                String sessionUserId = extractUserIdFromSession(session);
                if (sessionUserId != null && !sessionUserId.equals(request.getUserId())) {
                    if (webSocketHandler.sendMessage(session, notification)) {
                        notifiedMembers.add(sessionUserId);
                    } else {
                        failedMembers.add(sessionUserId);
                    }
                }
            }
            
            // ログ出力（通知成功/失敗したメンバーを含む）
            loggingService.logGameStart(request.getUserId(), request.getTeamspaceId(), partyId, 
                                       notifiedMembers, failedMembers);
            
            return ResponseEntity.ok(new StartGameResponse(partyId));
            
        } catch (BusinessException e) {
            loggingService.logGameStartFailed(request.getUserId(), e.getErrorCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Failed to start game", e);
            loggingService.logGameStartFailed(request.getUserId(), "INTERNAL_SERVER_ERROR", e.getMessage());
            throw new BusinessException("INTERNAL_SERVER_ERROR", "サーバーエラー", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    private String extractUserIdFromSession(WebSocketSession session) {
        return connectionManager.getUserId(session);
    }
}

