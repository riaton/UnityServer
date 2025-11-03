package com.game.matching.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class OrganizeTeamRequest {
    @NotBlank(message = "userId is required")
    @Size(max = 50, message = "userId must be 50 characters or less")
    private String userId;
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
}

