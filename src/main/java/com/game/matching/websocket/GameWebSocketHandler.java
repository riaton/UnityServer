package com.game.matching.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.matching.service.CognitoAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GameWebSocketHandler.class);
    
    private final WebSocketConnectionManager connectionManager;
    private final CognitoAuthService cognitoAuthService;
    private final ObjectMapper objectMapper;
    
    public GameWebSocketHandler(WebSocketConnectionManager connectionManager, 
                                CognitoAuthService cognitoAuthService) {
        this.connectionManager = connectionManager;
        this.cognitoAuthService = cognitoAuthService;
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        URI uri = session.getUri();
        if (uri == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        
        String query = uri.getQuery();
        String teamspaceId = extractTeamspaceId(query);
        String userId = extractUserId(query);
        
        if (teamspaceId == null || teamspaceId.isEmpty()) {
            logger.warn("WebSocket connection without teamspaceId, closing session");
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        
        if (userId == null || userId.isEmpty()) {
            logger.warn("WebSocket connection without userId, closing session");
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        
        // 接続を登録
        connectionManager.addConnection(teamspaceId, userId, session);
        logger.info("WebSocket connected for teamspace: {}, userId: {}", teamspaceId, userId);
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        connectionManager.removeConnection(session);
        logger.info("WebSocket closed: {}", status);
    }
    
    private String extractTeamspaceId(String query) {
        return extractQueryParam(query, "teamspaceId");
    }
    
    private String extractUserId(String query) {
        return extractQueryParam(query, "userId");
    }
    
    private String extractQueryParam(String query, String paramName) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        String[] params = query.split("&");
        for (String param : params) {
            String[] keyValue = param.split("=");
            if (keyValue.length == 2 && keyValue[0].equals(paramName)) {
                return keyValue[1];
            }
        }
        return null;
    }
    
    /**
     * メッセージを送信
     */
    public boolean sendMessage(WebSocketSession session, Object message) {
        try {
            if (session.isOpen()) {
                String json = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(json));
                return true;
            }
        } catch (IOException e) {
            logger.error("Failed to send WebSocket message", e);
        }
        return false;
    }
}

