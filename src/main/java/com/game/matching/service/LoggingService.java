package com.game.matching.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LoggingService {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingService.class);
    private final ObjectMapper objectMapper;
    
    public LoggingService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }
    
    // API①
    public void logTeamspaceCreated(String userId, String teamspaceId) {
        String message = String.format("[API①] TeamSpace created successfully - userId: %s, teamspaceId: %s, timestamp: %s",
                                       userId, teamspaceId, Instant.now().toString());
        logger.info(message);
        logStructured("TEAMSPACE_CREATED", userId, teamspaceId, null);
    }
    
    public void logTeamspaceCreateFailed(String userId, String errorCode, String errorMessage) {
        String message = String.format("[API①] Failed to create TeamSpace - userId: %s, error: %s, message: %s, timestamp: %s",
                                       userId, errorCode, errorMessage, Instant.now().toString());
        logger.error(message);
    }
    
    // API②
    public void logTeamspaceJoined(String userId, String teamspaceId) {
        String message = String.format("[API②] TeamSpace joined successfully - userId: %s, teamspaceId: %s, timestamp: %s",
                                       userId, teamspaceId, Instant.now().toString());
        logger.info(message);
        logStructured("TEAMSPACE_JOINED", userId, teamspaceId, null);
    }
    
    public void logTeamspaceJoinFailed(String userId, String errorCode, String errorMessage) {
        String message = String.format("[API②] Failed to join to TeamSpace - userId: %s, error: %s, message: %s, timestamp: %s",
                                       userId, errorCode, errorMessage, Instant.now().toString());
        logger.error(message);
    }
    
    // API③
    public void logTeamspaceLeft(String userId, String teamspaceId) {
        String message = String.format("[API③] TeamSpace left successfully - userId: %s, teamspaceId: %s, timestamp: %s",
                                       userId, teamspaceId, Instant.now().toString());
        logger.info(message);
        logStructured("TEAMSPACE_LEFT", userId, teamspaceId, null);
    }
    
    public void logTeamspaceLeaveFailed(String userId, String errorCode, String errorMessage) {
        String message = String.format("[API③] Failed to left from TeamSpace - userId: %s, error: %s, message: %s, timestamp: %s",
                                       userId, errorCode, errorMessage, Instant.now().toString());
        logger.error(message);
    }
    
    // API④
    public void logGameStart(String organizer, String teamspaceId, String partyId,
                            List<String> notifiedMembers, List<String> failedMembers) {
        String message = String.format("[API④] Game Start successfully - organizer: %s, teamspaceId: %s, partyId: %s, " +
                                       "notifiedMembers: %s, failedMembers: %s, timestamp: %s",
                                       organizer, teamspaceId, partyId, notifiedMembers, failedMembers, Instant.now().toString());
        logger.info(message);
        logStructured("GAME_START", organizer, teamspaceId, partyId);
    }
    
    public void logGameStartFailed(String userId, String errorCode, String errorMessage) {
        String message = String.format("[API④] Failed to start game - organizer: %s, error: %s, message: %s, timestamp: %s",
                                       userId, errorCode, errorMessage, Instant.now().toString());
        logger.error(message);
    }
    
    // API⑤
    public void logPartyJoined(String userId, String teamspaceId) {
        String message = String.format("[API⑤] Party joined successfully - userId: %s, teamspaceId: %s, timestamp: %s",
                                       userId, teamspaceId, Instant.now().toString());
        logger.info(message);
        logStructured("PARTY_JOINED", userId, teamspaceId, null);
    }
    
    public void logPartyJoinFailed(String userId, String errorCode, String errorMessage) {
        String message = String.format("[API⑤] Failed to join to Party - userId: %s, error: %s, message: %s, timestamp: %s",
                                       userId, errorCode, errorMessage, Instant.now().toString());
        logger.error(message);
    }
    
    private void logStructured(String eventType, String userId, String teamspaceId, String partyId) {
        try {
            Map<String, Object> logData = new HashMap<>();
            logData.put("level", "INFO");
            logData.put("eventType", eventType);
            logData.put("userId", userId);
            logData.put("teamspaceId", teamspaceId);
            if (partyId != null) {
                logData.put("partyId", partyId);
            }
            logData.put("timestamp", Instant.now().toString());
            
            String json = objectMapper.writeValueAsString(logData);
            logger.info(json);
        } catch (Exception e) {
            logger.warn("Failed to log structured data", e);
        }
    }
}

