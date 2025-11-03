package com.game.matching.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.matching.dto.*;
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
public class TeamController {
    
    private static final Logger logger = LoggerFactory.getLogger(TeamController.class);
    private static final int MAX_TEAM_MEMBERS = 4;
    
    private final RedisRepository redisRepository;
    private final WebSocketConnectionManager connectionManager;
    private final GameWebSocketHandler webSocketHandler;
    private final LoggingService loggingService;
    private final ObjectMapper objectMapper;
    
    public TeamController(RedisRepository redisRepository,
                         WebSocketConnectionManager connectionManager,
                         GameWebSocketHandler webSocketHandler,
                         LoggingService loggingService) {
        this.redisRepository = redisRepository;
        this.connectionManager = connectionManager;
        this.webSocketHandler = webSocketHandler;
        this.loggingService = loggingService;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * API①: チームスペース作成
     */
    @PostMapping("/organize_team")
    public ResponseEntity<OrganizeTeamResponse> organizeTeam(
            @Valid @RequestBody OrganizeTeamRequest request,
            HttpServletRequest httpRequest) {
        
        String tokenUserId = (String) httpRequest.getAttribute("userId");
        
        // トークンから取得したuserIdとrequest.bodyのuserIdが一致するかチェック
        if (!tokenUserId.equals(request.getUserId())) {
            throw new BusinessException("VALIDATION_ERROR", 
                                       "Token userId does not match request userId", 
                                       HttpStatus.BAD_REQUEST);
        }
        
        try {
            // ユーザーが既に他のチームに参加/主催していないかチェック
            Optional<Teamspace> existingAsOrganizer = redisRepository.findTeamspaceByOrganizer(request.getUserId());
            Optional<Teamspace> existingAsMember = redisRepository.findTeamspaceByMember(request.getUserId());
            
            if (existingAsOrganizer.isPresent() || existingAsMember.isPresent()) {
                throw new BusinessException("USER_ALREADY_IN_TEAM",
                                          "ユーザーは既に他のチームに参加しています",
                                          HttpStatus.CONFLICT);
            }
            
            // UUID4でteamspaceIdを生成
            String teamspaceId = UUID.randomUUID().toString();
            
            // Redisに保存
            Teamspace teamspace = new Teamspace(teamspaceId, request.getUserId());
            redisRepository.saveTeamspace(teamspace);
            
            // ログ出力
            loggingService.logTeamspaceCreated(request.getUserId(), teamspaceId);
            
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new OrganizeTeamResponse(teamspaceId));
                    
        } catch (BusinessException e) {
            loggingService.logTeamspaceCreateFailed(request.getUserId(), e.getErrorCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Failed to create teamspace", e);
            loggingService.logTeamspaceCreateFailed(request.getUserId(), "INTERNAL_SERVER_ERROR", e.getMessage());
            throw new BusinessException("INTERNAL_SERVER_ERROR", "サーバーエラー", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * API②: チーム参加
     */
    @PostMapping("/join_team")
    public ResponseEntity<Map<String, Object>> joinTeam(
            @Valid @RequestBody JoinTeamRequest request,
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
            
            // ユーザーが既に他のチームに参加/主催していないかチェック
            Optional<Teamspace> existingAsOrganizer = redisRepository.findTeamspaceByOrganizer(request.getUserId());
            Optional<Teamspace> existingAsMember = redisRepository.findTeamspaceByMember(request.getUserId());
            
            if (existingAsOrganizer.isPresent() || 
                (existingAsMember.isPresent() && !existingAsMember.get().getTeamspaceId().equals(request.getTeamspaceId()))) {
                throw new BusinessException("USER_ALREADY_IN_TEAM",
                                          "ユーザーは既に他のチームに参加しています",
                                          HttpStatus.CONFLICT);
            }
            
            // 既に同じteamspaceのメンバーかチェック
            if (teamspace.isMember(request.getUserId())) {
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
            teamspace.addMember(request.getUserId());
            redisRepository.saveTeamspace(teamspace);
            
            // WebSocket通知
            List<String> memberIds = new ArrayList<>(teamspace.getMembers());
            Map<String, Object> notification = Map.of("type", "memberList", "userIds", memberIds);
            
            Set<WebSocketSession> sessions = connectionManager.getConnections(request.getTeamspaceId());
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
            
            // ログ出力
            loggingService.logTeamspaceJoined(request.getUserId(), request.getTeamspaceId());
            
            return ResponseEntity.ok(Collections.emptyMap());
            
        } catch (BusinessException e) {
            loggingService.logTeamspaceJoinFailed(request.getUserId(), e.getErrorCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Failed to join teamspace", e);
            loggingService.logTeamspaceJoinFailed(request.getUserId(), "INTERNAL_SERVER_ERROR", e.getMessage());
            throw new BusinessException("INTERNAL_SERVER_ERROR", "サーバーエラー", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * API③: チーム脱退
     */
    @PostMapping("/leave_team")
    public ResponseEntity<Map<String, Object>> leaveTeam(
            @Valid @RequestBody LeaveTeamRequest request,
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
            
            // ユーザーがteamspaceのメンバーかどうかチェック
            if (!teamspace.isMember(request.getUserId())) {
                throw new BusinessException("NOT_A_MEMBER",
                                          "ユーザーはこのteamspaceのメンバーではありません",
                                          HttpStatus.CONFLICT);
            }
            
            // 主催者の場合
            if (teamspace.isOrganizer(request.getUserId())) {
                // teamspaceを削除
                redisRepository.deleteTeamspace(request.getTeamspaceId());
                loggingService.logTeamspaceLeft(request.getUserId(), request.getTeamspaceId());
                return ResponseEntity.ok(Collections.emptyMap());
            }
            
            // 参加者の場合
            teamspace.removeMember(request.getUserId());
            redisRepository.saveTeamspace(teamspace);
            
            // WebSocket通知
            List<String> memberIds = new ArrayList<>(teamspace.getMembers());
            Map<String, Object> notification = Map.of("type", "memberList", "userIds", memberIds);
            
            Set<WebSocketSession> sessions = connectionManager.getConnections(request.getTeamspaceId());
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
            
            // ログ出力
            loggingService.logTeamspaceLeft(request.getUserId(), request.getTeamspaceId());
            
            return ResponseEntity.ok(Collections.emptyMap());
            
        } catch (BusinessException e) {
            loggingService.logTeamspaceLeaveFailed(request.getUserId(), e.getErrorCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Failed to leave teamspace", e);
            loggingService.logTeamspaceLeaveFailed(request.getUserId(), "INTERNAL_SERVER_ERROR", e.getMessage());
            throw new BusinessException("INTERNAL_SERVER_ERROR", "サーバーエラー", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * API⑤: 既存パーティ(ゲームプレイ中)への参加
     */
    @PostMapping("/join_existing_party")
    public ResponseEntity<Map<String, Object>> joinExistingParty(
            @Valid @RequestBody JoinExistingPartyRequest request,
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
            
            // ユーザーが既に他のチームに参加/主催していないかチェック
            Optional<Teamspace> existingAsOrganizer = redisRepository.findTeamspaceByOrganizer(request.getUserId());
            Optional<Teamspace> existingAsMember = redisRepository.findTeamspaceByMember(request.getUserId());
            
            if (existingAsOrganizer.isPresent() || 
                (existingAsMember.isPresent() && !existingAsMember.get().getTeamspaceId().equals(request.getTeamspaceId()))) {
                throw new BusinessException("USER_ALREADY_IN_TEAM",
                                          "ユーザーは既に他のチームに参加しています",
                                          HttpStatus.CONFLICT);
            }
            
            // 既に同じteamspaceのメンバーかチェック
            if (teamspace.isMember(request.getUserId())) {
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
            teamspace.addMember(request.getUserId());
            redisRepository.saveTeamspace(teamspace);
            
            // ログ出力
            loggingService.logPartyJoined(request.getUserId(), request.getTeamspaceId());
            
            return ResponseEntity.ok(Collections.emptyMap());
            
        } catch (BusinessException e) {
            loggingService.logPartyJoinFailed(request.getUserId(), e.getErrorCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Failed to join existing party", e);
            loggingService.logPartyJoinFailed(request.getUserId(), "INTERNAL_SERVER_ERROR", e.getMessage());
            throw new BusinessException("INTERNAL_SERVER_ERROR", "サーバーエラー", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}

