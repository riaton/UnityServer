package com.game.matching.service;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.game.matching.dto.OrganizeTeamResponse;
import com.game.matching.exception.BusinessException;
import com.game.matching.model.Teamspace;
import com.game.matching.repository.RedisRepository;
import com.game.matching.websocket.GameWebSocketHandler;
import com.game.matching.websocket.WebSocketConnectionManager;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeamService - API①: チームスペース作成")
class TeamServiceTest {
    
    @Mock
    private RedisRepository redisRepository;
    
    @Mock
    private WebSocketConnectionManager connectionManager;
    
    @Mock
    private GameWebSocketHandler webSocketHandler;
    
    @Mock
    private LoggingService loggingService;
    
    @InjectMocks
    private TeamService teamService;
    
    private String userId;
    
    @BeforeEach
    void setUp() {
        userId = "user-123";
        
        // 正常系テスト用のデフォルトセットアップ: 既存のチームに参加していない状態
        when(redisRepository.findTeamspaceByOrganizer(userId))
            .thenReturn(Optional.empty());
        when(redisRepository.findTeamspaceByMember(userId))
            .thenReturn(Optional.empty());
    }
    
    @Test
    @DisplayName("正常系: 新規でチームスペースを作成できる")
    void organizeTeam_正常系() {
        // Given: セットアップは@BeforeEachで済んでいる（既存のチームに参加していない状態）
        // When: チームスペースを作成
        OrganizeTeamResponse response = teamService.organizeTeam(userId);
        
        // Then: レスポンスが正しく返される
        assertThat(response).isNotNull();
        assertThat(response.getTeamspaceId()).isNotNull();
        assertThat(response.getTeamspaceId()).matches(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
        );
        
        // Redisに保存されたことを確認
        verify(redisRepository, times(1)).saveTeamspace(any(Teamspace.class));
        
        // ログが出力されたことを確認
        verify(loggingService, times(1)).logTeamspaceCreated(
            eq(userId),
            anyString()
        );
        
        // エラーログが出力されていないことを確認
        verify(loggingService, never()).logTeamspaceCreateFailed(
            anyString(), anyString(), anyString()
        );
    }
    
    @Test
    @DisplayName("異常系: 既に主催者として参加している場合")
    void organizeTeam_既に主催者として参加中() {
        // Given: 既に主催者として参加している
        String existingTeamspaceId = UUID.randomUUID().toString();
        Teamspace existingTeamspace = new Teamspace(existingTeamspaceId, userId);
        
        when(redisRepository.findTeamspaceByOrganizer(userId))
            .thenReturn(Optional.of(existingTeamspace));
        
        // When & Then: USER_ALREADY_IN_TEAMエラーが発生
        assertThatThrownBy(() -> teamService.organizeTeam(userId))
            .isInstanceOf(BusinessException.class)
            .satisfies(exception -> {
                BusinessException be = (BusinessException) exception;
                assertThat(be.getErrorCode()).isEqualTo("USER_ALREADY_IN_TEAM");
                assertThat(be.getMessage()).isEqualTo("ユーザーは既に他のチームに参加しています");
                assertThat(be.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
            });
        
        // Redisに保存されていないことを確認
        verify(redisRepository, never()).saveTeamspace(any(Teamspace.class));
        
        // エラーログが出力されたことを確認
        verify(loggingService, times(1)).logTeamspaceCreateFailed(
            eq(userId),
            eq("USER_ALREADY_IN_TEAM"),
            anyString()
        );
        
        // 成功ログが出力されていないことを確認
        verify(loggingService, never()).logTeamspaceCreated(
            anyString(), anyString()
        );
    }
    
    @Test
    @DisplayName("異常系: 既にメンバーとして参加している場合")
    void organizeTeam_既にメンバーとして参加中() {
        // Given: 既にメンバーとして参加している
        String existingTeamspaceId = UUID.randomUUID().toString();
        Teamspace existingTeamspace = new Teamspace(existingTeamspaceId, "other-organizer");
        existingTeamspace.addMember(userId);
        
        when(redisRepository.findTeamspaceByOrganizer(userId))
            .thenReturn(Optional.empty());
        when(redisRepository.findTeamspaceByMember(userId))
            .thenReturn(Optional.of(existingTeamspace));
        
        // When & Then: USER_ALREADY_IN_TEAMエラーが発生
        assertThatThrownBy(() -> teamService.organizeTeam(userId))
            .isInstanceOf(BusinessException.class)
            .satisfies(exception -> {
                BusinessException be = (BusinessException) exception;
                assertThat(be.getErrorCode()).isEqualTo("USER_ALREADY_IN_TEAM");
                assertThat(be.getMessage()).isEqualTo("ユーザーは既に他のチームに参加しています");
                assertThat(be.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
            });
        
        // Redisに保存されていないことを確認
        verify(redisRepository, never()).saveTeamspace(any(Teamspace.class));
        
        // エラーログが出力されたことを確認
        verify(loggingService, times(1)).logTeamspaceCreateFailed(
            eq(userId),
            eq("USER_ALREADY_IN_TEAM"),
            anyString()
        );
        
        // 成功ログが出力されていないことを確認
        verify(loggingService, never()).logTeamspaceCreated(
            anyString(), anyString()
        );
    }
    
    @Test
    @DisplayName("異常系: Redis保存に失敗した場合")
    void organizeTeam_Redis保存失敗() {
        // Given: 既存のチームに参加していないが、Redis保存に失敗（セットアップは@BeforeEachで済んでいる）
        doThrow(new RuntimeException("Redis connection failed"))
            .when(redisRepository).saveTeamspace(any(Teamspace.class));
        
        // When & Then: INTERNAL_SERVER_ERRORエラーが発生
        assertThatThrownBy(() -> teamService.organizeTeam(userId))
            .isInstanceOf(BusinessException.class)
            .satisfies(exception -> {
                BusinessException be = (BusinessException) exception;
                assertThat(be.getErrorCode()).isEqualTo("INTERNAL_SERVER_ERROR");
                assertThat(be.getMessage()).isEqualTo("サーバーエラー");
                assertThat(be.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            });
        
        // エラーログが出力されたことを確認
        verify(loggingService, times(1)).logTeamspaceCreateFailed(
            eq(userId),
            eq("INTERNAL_SERVER_ERROR"),
            anyString()
        );
        
        // 成功ログが出力されていないことを確認
        verify(loggingService, never()).logTeamspaceCreated(
            anyString(), anyString()
        );
    }
    
    @Test
    @DisplayName("正常系: 生成されたteamspaceIdがUUID形式であること")
    void organizeTeam_teamspaceIdがUUID形式() {
        // When: チームスペースを作成
        OrganizeTeamResponse response = teamService.organizeTeam(userId);
        
        // Then: teamspaceIdが有効なUUID形式であること
        String teamspaceId = response.getTeamspaceId();
        assertThat(teamspaceId).isNotNull();
        
        // UUID形式の検証（ハイフンを含む36文字）
        assertThat(teamspaceId).matches(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
        );
        
        // UUIDとしてパースできること
        assertThatCode(() -> UUID.fromString(teamspaceId))
            .doesNotThrowAnyException();
    }
    
    @Test
    @DisplayName("正常系: 作成されたTeamspaceに主催者がメンバーとして含まれていること")
    void organizeTeam_主催者がメンバーに含まれる() {
        // When: チームスペースを作成
        teamService.organizeTeam(userId);
        
        // Then: 保存されたTeamspaceを検証
        verify(redisRepository, times(1)).saveTeamspace(argThat(teamspace -> {
            assertThat(teamspace.getOrganizer()).isEqualTo(userId);
            assertThat(teamspace.getMembers()).contains(userId);
            assertThat(teamspace.getMembers().size()).isEqualTo(1);
            assertThat(teamspace.getPartyId()).isNull();
            return true;
        }));
    }
}
