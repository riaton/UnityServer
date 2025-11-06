package com.game.matching.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketConnectionManager {
    
    // teamspaceId -> Set<WebSocketSession> のマップ
    private final Map<String, Set<WebSocketSession>> teamspaceConnections = new ConcurrentHashMap<>();
    
    // WebSocketSession -> teamspaceId のマップ（逆引き用）
    private final Map<WebSocketSession, String> sessionToTeamspace = new ConcurrentHashMap<>();
    
    // WebSocketSession -> userId のマップ
    private final Map<WebSocketSession, String> sessionToUserId = new ConcurrentHashMap<>();
    
    /**
     * 接続を追加
     */
    public void addConnection(String teamspaceId, String userId, WebSocketSession session) {
        teamspaceConnections.computeIfAbsent(teamspaceId, k -> ConcurrentHashMap.newKeySet()).add(session);
        sessionToTeamspace.put(session, teamspaceId);
        sessionToUserId.put(session, userId);
    }
    
    /**
     * 接続を削除
     */
    public void removeConnection(WebSocketSession session) {
        String teamspaceId = sessionToTeamspace.remove(session);
        sessionToUserId.remove(session);
        if (teamspaceId != null) {
            Set<WebSocketSession> sessions = teamspaceConnections.get(teamspaceId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    teamspaceConnections.remove(teamspaceId);
                }
            }
        }
    }
    
    /**
     * セッションからuserIdを取得
     */
    public String getUserId(WebSocketSession session) {
        return sessionToUserId.get(session);
    }
    
    /**
     * teamspaceIdに関連する全接続を取得
     */
    public Set<WebSocketSession> getConnections(String teamspaceId) {
        return teamspaceConnections.getOrDefault(teamspaceId, Collections.emptySet());
    }
}

