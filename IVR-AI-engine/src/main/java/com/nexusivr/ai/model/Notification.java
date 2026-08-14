package com.nexusivr.ai.model;

import java.sql.Timestamp;
import java.util.UUID;

public class Notification {

    private UUID id;
    private UUID tenantId;
    private UUID userId;
    private String message;
    private String linkUrl;
    private boolean read = false;
    private Timestamp createdAt;
    private String type;

    public Notification() {}

    public Notification(UUID id, UUID tenantId, UUID userId, String message, String linkUrl, boolean read, Timestamp createdAt, String type) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.message = message;
        this.linkUrl = linkUrl;
        this.read = read;
        this.createdAt = createdAt;
        this.type = type;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getLinkUrl() { return linkUrl; }
    public void setLinkUrl(String linkUrl) { this.linkUrl = linkUrl; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
