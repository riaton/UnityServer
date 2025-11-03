package com.game.matching.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Teamspace {
    private String teamspaceId;
    private String organizer;
    private List<String> members;
    private Instant createdAt;
    private String partyId;
    
    public Teamspace() {
        this.members = new ArrayList<>();
        this.createdAt = Instant.now();
    }
    
    public Teamspace(String teamspaceId, String organizer) {
        this.teamspaceId = teamspaceId;
        this.organizer = organizer;
        this.members = new ArrayList<>();
        this.members.add(organizer);
        this.createdAt = Instant.now();
        this.partyId = null;
    }
    
    // Getters and Setters
    public String getTeamspaceId() {
        return teamspaceId;
    }
    
    public void setTeamspaceId(String teamspaceId) {
        this.teamspaceId = teamspaceId;
    }
    
    public String getOrganizer() {
        return organizer;
    }
    
    public void setOrganizer(String organizer) {
        this.organizer = organizer;
    }
    
    public List<String> getMembers() {
        return members;
    }
    
    public void setMembers(List<String> members) {
        this.members = members;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public String getPartyId() {
        return partyId;
    }
    
    public void setPartyId(String partyId) {
        this.partyId = partyId;
    }
    
    public boolean isMember(String userId) {
        return members.contains(userId);
    }
    
    public boolean isOrganizer(String userId) {
        return organizer.equals(userId);
    }
    
    public void addMember(String userId) {
        if (!members.contains(userId)) {
            members.add(userId);
        }
    }
    
    public void removeMember(String userId) {
        members.remove(userId);
    }
}

