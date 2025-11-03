package com.game.matching.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class JoinExistingPartyRequest {
    @NotBlank(message = "teamspaceId is required")
    @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
             message = "teamspaceId must be a valid UUID")
    private String teamspaceId;
    
    @NotBlank(message = "userId is required")
    @Size(max = 50, message = "userId must be 50 characters or less")
    private String userId;
    
    public String getTeamspaceId() {
        return teamspaceId;
    }
    
    public void setTeamspaceId(String teamspaceId) {
        this.teamspaceId = teamspaceId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
}

