package com.game.matching.repository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.game.matching.model.Teamspace;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Testcontainers
@DisplayName("RedisRepository - saveTeamspace")
class RedisRepositoryTest {
    
    @Container
    private static final GenericContainer<?> redisContainer = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    
    private JedisPool jedisPool;
    private RedisRepository redisRepository;
    
    @BeforeEach
    void setUp() {
        // Testcontainersで起動したRedisに接続
        String host = redisContainer.getHost();
        int port = redisContainer.getMappedPort(6379);
        
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);
        poolConfig.setMaxIdle(5);
        poolConfig.setMinIdle(1);
        
        jedisPool = new JedisPool(poolConfig, host, port);
        redisRepository = new RedisRepository(jedisPool);
    }
    
    @AfterEach
    void tearDown() {
        // テストデータをクリーンアップ
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushDB();
        }
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }
    
    @Test
    @DisplayName("正常系: Teamspaceを保存できる")
    void saveTeamspace_正常系() {
        // Given: テストデータを作成
        String teamspaceId = UUID.randomUUID().toString();
        String organizer = "user-123";
        Teamspace teamspace = new Teamspace(teamspaceId, organizer);
        
        // When: Teamspaceを保存
        redisRepository.saveTeamspace(teamspace);
        
        // Then: 保存されたデータを取得して検証
        Optional<Teamspace> retrieved = redisRepository.getTeamspace(teamspaceId);
        assertThat(retrieved).isPresent();
        
        Teamspace saved = retrieved.get();
        assertThat(saved.getTeamspaceId()).isEqualTo(teamspaceId);
        assertThat(saved.getOrganizer()).isEqualTo(organizer);
        assertThat(saved.getMembers()).contains(organizer);
        assertThat(saved.getMembers().size()).isEqualTo(1);
        assertThat(saved.getPartyId()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }    
    
    @Test
    @DisplayName("正常系: TTLが24時間に設定されている")
    void saveTeamspace_TTLが24時間() {
        // Given: テストデータを作成
        String teamspaceId = UUID.randomUUID().toString();
        String organizer = "user-123";
        Teamspace teamspace = new Teamspace(teamspaceId, organizer);
        
        // When: Teamspaceを保存
        redisRepository.saveTeamspace(teamspace);
        
        // Then: TTLが設定されている（24時間 = 86400秒）
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "teamspace:" + teamspaceId;
            long ttl = jedis.ttl(key);
            // TTLは86400秒（24時間）に設定されている
            // ただし、実行時間の差で少し減っている可能性があるので、86000秒以上であることを確認
            assertThat(ttl).isGreaterThan(86000);
            assertThat(ttl).isLessThanOrEqualTo(86400);
        }
    }
    
    @Test
    @DisplayName("正常系: 複数のTeamspaceを保存できる")
    void saveTeamspace_複数保存可能() {
        // Given: 複数のテストデータを作成
        String teamspaceId1 = UUID.randomUUID().toString();
        String teamspaceId2 = UUID.randomUUID().toString();
        Teamspace teamspace1 = new Teamspace(teamspaceId1, "user-1");
        Teamspace teamspace2 = new Teamspace(teamspaceId2, "user-2");
        
        // When: 複数のTeamspaceを保存
        redisRepository.saveTeamspace(teamspace1);
        redisRepository.saveTeamspace(teamspace2);
        
        // Then: 両方とも取得できる
        Optional<Teamspace> retrieved1 = redisRepository.getTeamspace(teamspaceId1);
        Optional<Teamspace> retrieved2 = redisRepository.getTeamspace(teamspaceId2);
        
        assertThat(retrieved1).isPresent();
        assertThat(retrieved1.get().getTeamspaceId()).isEqualTo(teamspaceId1);
        assertThat(retrieved1.get().getOrganizer()).isEqualTo("user-1");
        
        assertThat(retrieved2).isPresent();
        assertThat(retrieved2.get().getTeamspaceId()).isEqualTo(teamspaceId2);
        assertThat(retrieved2.get().getOrganizer()).isEqualTo("user-2");
    }
    
    @Test
    @DisplayName("正常系: キーのプレフィックスが正しい")
    void saveTeamspace_キープレフィックスが正しい() {
        // Given: テストデータを作成
        String teamspaceId = UUID.randomUUID().toString();
        Teamspace teamspace = new Teamspace(teamspaceId, "user-123");
        
        // When: Teamspaceを保存
        redisRepository.saveTeamspace(teamspace);
        
        // Then: キーが "teamspace:{teamspaceId}" の形式で保存されている
        try (Jedis jedis = jedisPool.getResource()) {
            String expectedKey = "teamspace:" + teamspaceId;
            String value = jedis.get(expectedKey);
            assertThat(value).isNotNull();
            assertThat(value).isNotEmpty();
        }
    }
}
