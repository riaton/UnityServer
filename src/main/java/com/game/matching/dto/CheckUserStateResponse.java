package com.game.matching.dto;

public class CheckUserStateResponse {
    private boolean organizingParty;
    private boolean joiningAnotherParty;
    private boolean nowGamePlaying;
    private String teamspaceId;
    private String partyId;
    
    public CheckUserStateResponse() {
        this.organizingParty = false;
        this.joiningAnotherParty = false;
        this.nowGamePlaying = false;
        this.teamspaceId = "";
        this.partyId = "";
    }
    
    public CheckUserStateResponse(boolean organizingParty, boolean joiningAnotherParty, 
                                  boolean nowGamePlaying, String teamspaceId, String partyId) {
        this.organizingParty = organizingParty;
        this.joiningAnotherParty = joiningAnotherParty;
        this.nowGamePlaying = nowGamePlaying;
        this.teamspaceId = teamspaceId != null ? teamspaceId : "";
        this.partyId = partyId != null ? partyId : "";
    }
    
    public boolean isOrganizingParty() {
        return organizingParty;
    }
    
    public void setOrganizingParty(boolean organizingParty) {
        this.organizingParty = organizingParty;
    }
    
    public boolean isJoiningAnotherParty() {
        return joiningAnotherParty;
    }
    
    public void setJoiningAnotherParty(boolean joiningAnotherParty) {
        this.joiningAnotherParty = joiningAnotherParty;
    }
    
    public boolean isNowGamePlaying() {
        return nowGamePlaying;
    }
    
    public void setNowGamePlaying(boolean nowGamePlaying) {
        this.nowGamePlaying = nowGamePlaying;
    }
    
    public String getTeamspaceId() {
        return teamspaceId;
    }
    
    public void setTeamspaceId(String teamspaceId) {
        this.teamspaceId = teamspaceId != null ? teamspaceId : "";
    }
    
    public String getPartyId() {
        return partyId;
    }
    
    public void setPartyId(String partyId) {
        this.partyId = partyId != null ? partyId : "";
    }
}

