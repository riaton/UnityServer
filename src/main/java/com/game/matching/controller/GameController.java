package com.game.matching.controller;

import com.game.matching.dto.CheckUserStateResponse;
import com.game.matching.dto.ListJoiningPartyUsersResponse;
import com.game.matching.dto.StartGameRequest;
import com.game.matching.dto.StartGameResponse;
import com.game.matching.exception.BusinessException;
import com.game.matching.service.GameService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class GameController {
    
    private final GameService gameService;
    
    public GameController(GameService gameService) {
        this.gameService = gameService;
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
        
        StartGameResponse response = gameService.startGame(request.getUserId(), request.getTeamspaceId());
        return ResponseEntity.ok(response);
    }
    
    /**
     * API⑥: ユーザー状態確認
     */
    @GetMapping("/check_user_state")
    public ResponseEntity<CheckUserStateResponse> checkUserState(
            @RequestParam("userId") String userId,
            HttpServletRequest httpRequest) {
        
        String tokenUserId = (String) httpRequest.getAttribute("userId");
        
        // トークンから取得したuserIdとクエリパラメータのuserIdが一致するかチェック
        if (!tokenUserId.equals(userId)) {
            throw new BusinessException("VALIDATION_ERROR",
                                       "Token userId does not match query parameter userId",
                                       HttpStatus.BAD_REQUEST);
        }
        
        CheckUserStateResponse response = gameService.checkUserState(userId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * API⑦: 参加者一覧取得
     */
    @GetMapping("/list_joining_party_users")
    public ResponseEntity<ListJoiningPartyUsersResponse> listJoiningPartyUsers(
            @RequestParam("teamspaceId") String teamspaceId,
            @RequestParam("userId") String userId,
            HttpServletRequest httpRequest) {
        
        String tokenUserId = (String) httpRequest.getAttribute("userId");
        
        // トークンから取得したuserIdとクエリパラメータのuserIdが一致するかチェック
        if (!tokenUserId.equals(userId)) {
            throw new BusinessException("VALIDATION_ERROR",
                                       "Token userId does not match query parameter userId",
                                       HttpStatus.BAD_REQUEST);
        }
        
        ListJoiningPartyUsersResponse response = gameService.listJoiningPartyUsers(userId, teamspaceId);
        return ResponseEntity.ok(response);
    }
}

