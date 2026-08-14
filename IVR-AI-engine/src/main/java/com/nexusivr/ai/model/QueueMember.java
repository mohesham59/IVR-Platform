package com.nexusivr.ai.model;

import java.sql.Timestamp;
import java.util.UUID;

public class QueueMember {

    private UUID id;
    private UUID queueId;
    private UUID agentId;
    private int penalty = 0;
    private Timestamp addedAt;

    // Transient agent details
    private String agentUsername;
    private String agentEmail;
    private String agentState = "available";

    public QueueMember() {}

    public QueueMember(UUID id, UUID queueId, UUID agentId, int penalty, Timestamp addedAt) {
        this.id = id;
        this.queueId = queueId;
        this.agentId = agentId;
        this.penalty = penalty;
        this.addedAt = addedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getQueueId() { return queueId; }
    public void setQueueId(UUID queueId) { this.queueId = queueId; }

    public UUID getAgentId() { return agentId; }
    public void setAgentId(UUID agentId) { this.agentId = agentId; }

    public int getPenalty() { return penalty; }
    public void setPenalty(int penalty) { this.penalty = penalty; }

    public Timestamp getAddedAt() { return addedAt; }
    public void setAddedAt(Timestamp addedAt) { this.addedAt = addedAt; }

    public String getAgentUsername() { return agentUsername; }
    public void setAgentUsername(String agentUsername) { this.agentUsername = agentUsername; }

    public String getAgentEmail() { return agentEmail; }
    public void setAgentEmail(String agentEmail) { this.agentEmail = agentEmail; }

    public String getAgentState() { return agentState; }
    public void setAgentState(String agentState) { this.agentState = agentState; }
}
