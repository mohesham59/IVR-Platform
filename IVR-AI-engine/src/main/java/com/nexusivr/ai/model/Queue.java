package com.nexusivr.ai.model;

import java.sql.Timestamp;
import java.util.UUID;

public class Queue {

    private UUID id;
    private UUID tenantId;
    private String name;
    private String strategy;
    private int wrapUpTimeSeconds = 15;
    private int maxWaitSeconds = 300;
    private String musicOnHold = "default";
    private String overflowAction = "voicemail";
    private String businessHours;
    private String status = "active";
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Transient live AMI stats
    private int waitingCalls = 0;
    private int avgWaitSeconds = 0;
    private int memberCount = 0;
    private int activeMembers = 0;

    public Queue() {}

    public Queue(UUID id, UUID tenantId, String name, String strategy, int wrapUpTimeSeconds,
                 int maxWaitSeconds, String musicOnHold, String overflowAction,
                 String businessHours, String status, Timestamp createdAt, Timestamp updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.strategy = strategy;
        this.wrapUpTimeSeconds = wrapUpTimeSeconds;
        this.maxWaitSeconds = maxWaitSeconds;
        this.musicOnHold = musicOnHold;
        this.overflowAction = overflowAction;
        this.businessHours = businessHours;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }

    public int getWrapUpTimeSeconds() { return wrapUpTimeSeconds; }
    public void setWrapUpTimeSeconds(int wrapUpTimeSeconds) { this.wrapUpTimeSeconds = wrapUpTimeSeconds; }

    public int getMaxWaitSeconds() { return maxWaitSeconds; }
    public void setMaxWaitSeconds(int maxWaitSeconds) { this.maxWaitSeconds = maxWaitSeconds; }

    public String getMusicOnHold() { return musicOnHold; }
    public void setMusicOnHold(String musicOnHold) { this.musicOnHold = musicOnHold; }

    public String getOverflowAction() { return overflowAction; }
    public void setOverflowAction(String overflowAction) { this.overflowAction = overflowAction; }

    public String getBusinessHours() { return businessHours; }
    public void setBusinessHours(String businessHours) { this.businessHours = businessHours; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public int getWaitingCalls() { return waitingCalls; }
    public void setWaitingCalls(int waitingCalls) { this.waitingCalls = waitingCalls; }

    public int getAvgWaitSeconds() { return avgWaitSeconds; }
    public void setAvgWaitSeconds(int avgWaitSeconds) { this.avgWaitSeconds = avgWaitSeconds; }

    public int getMemberCount() { return memberCount; }
    public void setMemberCount(int memberCount) { this.memberCount = memberCount; }

    public int getActiveMembers() { return activeMembers; }
    public void setActiveMembers(int activeMembers) { this.activeMembers = activeMembers; }
}
