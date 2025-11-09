package com.game.matching.controller;

import com.game.matching.dto.*;
import com.game.matching.exception.BusinessException;
import com.game.matching.service.TeamService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class TeamController {
    
    private final TeamService teamService;
    
    public TeamController(TeamService teamService) {
        this.teamService = teamService;
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
        
        OrganizeTeamResponse response = teamService.organizeTeam(request.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
        
        teamService.joinTeam(request.getUserId(), request.getTeamspaceId());
        return ResponseEntity.ok(Collections.emptyMap());
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
        
        teamService.leaveTeam(request.getUserId(), request.getTeamspaceId());
        return ResponseEntity.ok(Collections.emptyMap());
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
        
        teamService.joinExistingParty(request.getUserId(), request.getTeamspaceId());
        return ResponseEntity.ok(Collections.emptyMap());
    }
}

