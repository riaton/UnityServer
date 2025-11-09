package com.game.matching.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.game.matching.model.Teamspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Repository
public class RedisRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisRepository.class);
    private static final String TEAMSPACE_KEY_PREFIX = "teamspace:";
    private static final int TTL_HOURS = 24;
    
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    
    public RedisRepository(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    /**
     * Teamspaceを保存
     */
    public void saveTeamspace(Teamspace teamspace) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = TEAMSPACE_KEY_PREFIX + teamspace.getTeamspaceId();
            String json = objectMapper.writeValueAsString(teamspace);
            jedis.setex(key, TTL_HOURS * 3600, json);
            logger.debug("Saved teamspace: {}", key);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize teamspace", e);
            throw new RuntimeException("Failed to save teamspace", e);
        }
    }
    
    /**
     * Teamspaceを取得
     */
    public Optional<Teamspace> getTeamspace(String teamspaceId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = TEAMSPACE_KEY_PREFIX + teamspaceId;
            String json = jedis.get(key);
            if (json == null) {
                return Optional.empty();
            }
            Teamspace teamspace = objectMapper.readValue(json, Teamspace.class);
            return Optional.of(teamspace);
        } catch (JsonProcessingException e) {
            logger.error("Failed to deserialize teamspace", e);
            return Optional.empty();
        }
    }
    
    /**
     * Teamspaceを削除
     */
    public void deleteTeamspace(String teamspaceId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = TEAMSPACE_KEY_PREFIX + teamspaceId;
            jedis.del(key);
            logger.debug("Deleted teamspace: {}", key);
        }
    }
    
    /**
     * ユーザーが主催しているteamspaceを検索
     */
    public Optional<Teamspace> findTeamspaceByOrganizer(String userId) {
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> keys = jedis.keys(TEAMSPACE_KEY_PREFIX + "*");
            for (String key : keys) {
                String json = jedis.get(key);
                if (json != null) {
                    try {
                        Teamspace teamspace = objectMapper.readValue(json, Teamspace.class);
                        if (teamspace.getOrganizer().equals(userId)) {
                            return Optional.of(teamspace);
                        }
                    } catch (JsonProcessingException e) {
                        logger.warn("Failed to parse teamspace: {}", key);
                    }
                }
            }
        }
        return Optional.empty();
    }
    
    /**
     * ユーザーがメンバーとして参加しているteamspaceを検索
     */
    public Optional<Teamspace> findTeamspaceByMember(String userId) {
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> keys = jedis.keys(TEAMSPACE_KEY_PREFIX + "*");
            for (String key : keys) {
                String json = jedis.get(key);
                if (json != null) {
                    try {
                        Teamspace teamspace = objectMapper.readValue(json, Teamspace.class);
                        if (teamspace.getMembers().contains(userId)) {
                            return Optional.of(teamspace);
                        }
                    } catch (JsonProcessingException e) {
                        logger.warn("Failed to parse teamspace: {}", key);
                    }
                }
            }
        }
        return Optional.empty();
    }
    
    /**
     * すべてのteamspaceを取得（API⑥用）
     */
    public List<Teamspace> getAllTeamspaces() {
        List<Teamspace> teamspaces = new ArrayList<>();
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> keys = jedis.keys(TEAMSPACE_KEY_PREFIX + "*");
            for (String key : keys) {
                String json = jedis.get(key);
                if (json != null) {
                    try {
                        Teamspace teamspace = objectMapper.readValue(json, Teamspace.class);
                        teamspaces.add(teamspace);
                    } catch (JsonProcessingException e) {
                        logger.warn("Failed to parse teamspace: {}", key);
                    }
                }
            }
        }
        return teamspaces;
    }
}

