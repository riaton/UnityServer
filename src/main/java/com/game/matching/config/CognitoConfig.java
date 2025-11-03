package com.game.matching.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CognitoConfig {
    
    @Value("${cognito.user-pool-id}")
    private String userPoolId;
    
    @Value("${cognito.region}")
    private String region;
    
    @Value("${cognito.jwks-url}")
    private String jwksUrl;
    
    public String getUserPoolId() {
        return userPoolId;
    }
    
    public String getRegion() {
        return region;
    }
    
    public String getJwksUrl() {
        return jwksUrl;
    }
}

