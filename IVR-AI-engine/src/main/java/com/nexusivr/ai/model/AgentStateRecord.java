package com.nexusivr.ai.model;

import java.sql.Timestamp;
import java.util.UUID;

public class AgentStateRecord {

    private UUID id;
    private UUID agentId;
    private String currentState = "available"; // available, in_call, paused, offline
    private Timestamp stateChangedAt;
    private UUID currentQueueId;

    // Transient fields
    private String username;
    private String email;

    public AgentStateRecord() {}

    public AgentStateRecord(UUID id, UUID agentId, String currentState, Timestamp stateChangedAt, UUID currentQueueId) {
        this.id = id;
        this.agentId = agentId;
        this.currentState = currentState;
        this.stateChangedAt = stateChangedAt;
        this.currentQueueId = currentQueueId;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAgentId() { return agentId; }
    public void setAgentId(UUID agentId) { this.agentId = agentId; }

    public String getCurrentState() { return currentState; }
    public void setCurrentState(String currentState) { this.currentState = currentState; }

    public Timestamp getStateChangedAt() { return stateChangedAt; }
    public void setStateChangedAt(Timestamp stateChangedAt) { this.stateChangedAt = stateChangedAt; }

    public UUID getCurrentQueueId() { return currentQueueId; }
    public void setCurrentQueueId(UUID currentQueueId) { this.currentQueueId = currentQueueId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
