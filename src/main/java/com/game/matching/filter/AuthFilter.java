package com.game.matching.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.matching.dto.ErrorResponse;
import com.game.matching.service.CognitoAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AuthFilter extends OncePerRequestFilter {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthFilter.class);
    
    private final CognitoAuthService cognitoAuthService;
    private final ObjectMapper objectMapper;
    
    public AuthFilter(CognitoAuthService cognitoAuthService) {
        this.cognitoAuthService = cognitoAuthService;
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        // 開発用バイパス: AUTH_BYPASS=true の場合はJWT検証をスキップ
        String authBypass = System.getenv("AUTH_BYPASS");
        if ("true".equalsIgnoreCase(authBypass)) {
            String debugUserId = request.getHeader("X-Debug-UserId");
            if (debugUserId == null || debugUserId.trim().isEmpty()) {
                debugUserId = "local-user";
            }
            request.setAttribute("userId", debugUserId);
            request.setAttribute("token", "bypassed");
            filterChain.doFilter(request, response);
            return;
        }
        
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendErrorResponse(response, HttpStatus.UNAUTHORIZED, "AUTHORIZATION_FAILED", 
                            "Authorization header is required");
            return;
        }
        
        String token = authHeader.substring(7);
        
        try {
            String userId = cognitoAuthService.extractUserId(token);
            request.setAttribute("userId", userId);
            request.setAttribute("token", token);
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            logger.error("Authentication failed: {}", e.getMessage());
            sendErrorResponse(response, HttpStatus.UNAUTHORIZED, "AUTHORIZATION_FAILED", 
                            "Invalid or expired token");
        }
    }
    
    private void sendErrorResponse(HttpServletResponse response, HttpStatus status, 
                                  String errorCode, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        
        ErrorResponse errorResponse = new ErrorResponse(errorCode, message);
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}

