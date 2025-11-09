package com.game.matching.dto;

import java.util.List;

public class ListJoiningPartyUsersResponse {
    private List<String> userIds;
    
    public ListJoiningPartyUsersResponse() {
    }
    
    public ListJoiningPartyUsersResponse(List<String> userIds) {
        this.userIds = userIds;
    }
    
    public List<String> getUserIds() {
        return userIds;
    }
    
    public void setUserIds(List<String> userIds) {
        this.userIds = userIds;
    }
}

