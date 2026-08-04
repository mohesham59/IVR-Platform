package com.nexusivr.ai.dto;

import java.util.UUID;

/**
 * Data Transfer Object for tenant MVP analytics summary.
 */
public class AnalyticsResponse {

    private UUID tenantId;
    private long totalSessions;
    private long activeSessions;
    private long totalMessages;
    private boolean degraded;

    public AnalyticsResponse() {
    }

    public AnalyticsResponse(UUID tenantId, long totalSessions, long activeSessions, long totalMessages) {
        this(tenantId, totalSessions, activeSessions, totalMessages, false);
    }

    public AnalyticsResponse(UUID tenantId, long totalSessions, long activeSessions, long totalMessages, boolean degraded) {
        this.tenantId = tenantId;
        this.totalSessions = totalSessions;
        this.activeSessions = activeSessions;
        this.totalMessages = totalMessages;
        this.degraded = degraded;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public long getTotalSessions() {
        return totalSessions;
    }

    public void setTotalSessions(long totalSessions) {
        this.totalSessions = totalSessions;
    }

    public long getActiveSessions() {
        return activeSessions;
    }

    public void setActiveSessions(long activeSessions) {
        this.activeSessions = activeSessions;
    }

    public long getTotalMessages() {
        return totalMessages;
    }

    public void setTotalMessages(long totalMessages) {
        this.totalMessages = totalMessages;
    }

    public boolean isDegraded() {
        return degraded;
    }

    public void setDegraded(boolean degraded) {
        this.degraded = degraded;
    }
}
