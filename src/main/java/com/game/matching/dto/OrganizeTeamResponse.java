package com.game.matching.dto;

public class OrganizeTeamResponse {
    private String teamspaceId;
    
    public OrganizeTeamResponse() {
    }
    
    public OrganizeTeamResponse(String teamspaceId) {
        this.teamspaceId = teamspaceId;
    }
    
    public String getTeamspaceId() {
        return teamspaceId;
    }
    
    public void setTeamspaceId(String teamspaceId) {
        this.teamspaceId = teamspaceId;
    }
}

