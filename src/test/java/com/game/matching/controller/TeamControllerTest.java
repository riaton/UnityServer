package com.game.matching.controller;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.matching.dto.OrganizeTeamRequest;
import com.game.matching.dto.OrganizeTeamResponse;
import com.game.matching.exception.GlobalExceptionHandler;
import com.game.matching.service.CognitoAuthService;
import com.game.matching.service.TeamService;

@WebMvcTest(TeamController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(TeamControllerTest.TestConfig.class)
@DisplayName("TeamController - API①: チームスペース作成")
class TeamControllerTest {
    
    @TestConfiguration
    static class TestConfig {
        @Bean
        public GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }
    }
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private TeamService teamService;
    
    @MockBean
    private CognitoAuthService cognitoAuthService;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Test
    @DisplayName("正常系: トークンのuserIdとリクエストボディのuserIdが一致する場合")
    void organizeTeam_正常系() throws Exception {
        // Given: テストデータを準備
        String userId = "user-123";
        String teamspaceId = UUID.randomUUID().toString();
        OrganizeTeamRequest request = new OrganizeTeamRequest();
        request.setUserId(userId);
        
        OrganizeTeamResponse response = new OrganizeTeamResponse(teamspaceId);
        
        // Serviceのモック設定
        when(teamService.organizeTeam(eq(userId))).thenReturn(response);
        
        // When & Then: APIを呼び出して検証
        mockMvc.perform(post("/api/organize_team")
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.teamspaceId").value(teamspaceId));
    }
    
    @Test
    @DisplayName("異常系: トークンのuserIdとリクエストボディのuserIdが一致しない場合")
    void organizeTeam_トークンとリクエストのuserId不一致() throws Exception {
        // Given: トークンのuserIdとリクエストボディのuserIdが異なる
        String tokenUserId = "user-123";
        String requestUserId = "user-456";
        OrganizeTeamRequest request = new OrganizeTeamRequest();
        request.setUserId(requestUserId);
        
        // When & Then: 400 Bad Requestが返される
        mockMvc.perform(post("/api/organize_team")
                .requestAttr("userId", tokenUserId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Token userId does not match request userId"));
    }
    
    @ParameterizedTest
    @MethodSource("validationTestCases")
    @DisplayName("バリデーション: userIdの境界値・不正値テスト")
    void organizeTeam_バリデーションテスト(String testCase, String userId, boolean shouldSucceed) throws Exception {
        // Given: テストデータを準備
        String tokenUserId = (userId != null && !userId.isEmpty()) ? userId : "user-123";
        OrganizeTeamRequest request = new OrganizeTeamRequest();
        request.setUserId(userId);
        
        if (shouldSucceed) {
            // 正常系: Serviceのモック設定
            String teamspaceId = UUID.randomUUID().toString();
            OrganizeTeamResponse response = new OrganizeTeamResponse(teamspaceId);
            when(teamService.organizeTeam(eq(userId))).thenReturn(response);
            
            // When & Then: 正常に処理される
            mockMvc.perform(post("/api/organize_team")
                    .requestAttr("userId", tokenUserId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.teamspaceId").value(teamspaceId));
        } else {
            // 異常系: 400 Bad Requestが返される（バリデーションエラー）
            mockMvc.perform(post("/api/organize_team")
                    .requestAttr("userId", tokenUserId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }
    
    static Stream<Arguments> validationTestCases() {
        return Stream.of(
            // 異常系: バリデーションエラー
            Arguments.of("userIdが空", "", false),
            Arguments.of("userIdがnull", null, false),
            Arguments.of("userIdが50文字超過", "a".repeat(51), false),
            // 正常系: 境界値テスト
            Arguments.of("userIdが50文字ちょうど", "a".repeat(50), true)
        );
    }
    
    @Test
    @DisplayName("異常系: Service層でUSER_ALREADY_IN_TEAMエラーが発生した場合")
    void organizeTeam_Service層でUSER_ALREADY_IN_TEAMエラー() throws Exception {
        // Given: Service層でUSER_ALREADY_IN_TEAMエラーが発生
        String userId = "user-123";
        OrganizeTeamRequest request = new OrganizeTeamRequest();
        request.setUserId(userId);
        
        com.game.matching.exception.BusinessException exception = 
            new com.game.matching.exception.BusinessException(
                "USER_ALREADY_IN_TEAM",
                "ユーザーは既に他のチームに参加しています",
                org.springframework.http.HttpStatus.CONFLICT
            );
        
        when(teamService.organizeTeam(eq(userId))).thenThrow(exception);
        
        // When & Then: 409 Conflictが返される
        mockMvc.perform(post("/api/organize_team")
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("USER_ALREADY_IN_TEAM"))
                .andExpect(jsonPath("$.message").value("ユーザーは既に他のチームに参加しています"));
    }
}
