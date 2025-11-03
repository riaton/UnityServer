package com.game.matching.service;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.game.matching.config.CognitoConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.interfaces.RSAPublicKey;

@Service
public class CognitoAuthService {
    
    private static final Logger logger = LoggerFactory.getLogger(CognitoAuthService.class);
    
    private final CognitoConfig cognitoConfig;
    private final JwkProvider jwkProvider;
    
    public CognitoAuthService(CognitoConfig cognitoConfig) {
        this.cognitoConfig = cognitoConfig;
        this.jwkProvider = new JwkProviderBuilder(cognitoConfig.getJwksUrl()).build();
    }
    
    /**
     * Cognitoアクセストークンを検証し、ユーザーID（sub）を取得
     * 
     * @param token アクセストークン
     * @return ユーザーID（sub）
     * @throws Exception トークンが無効な場合
     */
    public String extractUserId(String token) throws Exception {
        try {
            DecodedJWT decodedJWT = JWT.decode(token);
            String kid = decodedJWT.getKeyId();
            
            // JWKSから公開鍵を取得
            Jwk jwk = jwkProvider.get(kid);
            Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null);
            
            // トークンを検証
            JWT.require(algorithm)
                .withIssuer("https://cognito-idp." + cognitoConfig.getRegion() + ".amazonaws.com/" + cognitoConfig.getUserPoolId())
                .build()
                .verify(token);
            
            // sub（ユーザーID）を取得
            String userId = decodedJWT.getSubject();
            if (userId == null || userId.isEmpty()) {
                throw new Exception("Token does not contain subject");
            }
            
            return userId;
        } catch (Exception e) {
            logger.error("Failed to verify token: {}", e.getMessage());
            throw new Exception("Invalid token: " + e.getMessage(), e);
        }
    }
}

