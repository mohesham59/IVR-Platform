package com.nexusivr.ai.model;

import java.sql.Timestamp;
import java.util.UUID;

public class CallLog {

    private UUID id;
    private String sessionId;
    private UUID tenantId;
    private String callerId;
    private String scenarioName;
    private String status; // ANSWERED, MISSED, BUSY, FAILED, IN_PROGRESS
    private Timestamp startTime;
    private Timestamp endTime;
    private int duration = 0;
    private String lastNode;

    public CallLog() {}

    public CallLog(UUID id, String sessionId, UUID tenantId, String callerId, String scenarioName,
                   String status, Timestamp startTime, Timestamp endTime, int duration, String lastNode) {
        this.id = id;
        this.sessionId = sessionId;
        this.tenantId = tenantId;
        this.callerId = callerId;
        this.scenarioName = scenarioName;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
        this.duration = duration;
        this.lastNode = lastNode;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getCallerId() { return callerId; }
    public void setCallerId(String callerId) { this.callerId = callerId; }

    public String getScenarioName() { return scenarioName; }
    public void setScenarioName(String scenarioName) { this.scenarioName = scenarioName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getStartTime() { return startTime; }
    public void setStartTime(Timestamp startTime) { this.startTime = startTime; }

    public Timestamp getEndTime() { return endTime; }
    public void setEndTime(Timestamp endTime) { this.endTime = endTime; }

    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }

    public String getLastNode() { return lastNode; }
    public void setLastNode(String lastNode) { this.lastNode = lastNode; }
}
