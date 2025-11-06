package com.game.matching.dto;

public class StartGameResponse {
    private String partyId;
    
    public StartGameResponse() {
    }
    
    public StartGameResponse(String partyId) {
        this.partyId = partyId;
    }
    
    public String getPartyId() {
        return partyId;
    }
    
    public void setPartyId(String partyId) {
        this.partyId = partyId;
    }
}

