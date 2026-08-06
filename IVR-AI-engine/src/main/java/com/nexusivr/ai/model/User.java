package com.nexusivr.ai.model;

import java.sql.Timestamp;

public class User {
    private String id;
    private String activeTenantId;
    private String email;
    private String passwordHash;
    private boolean isSuperadmin;
    private String username;
    private String status;
    private Timestamp lastLoginAt;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public User() {}

    public User(String id, String activeTenantId, String email, String passwordHash, boolean isSuperadmin, String username, String status, Timestamp lastLoginAt, Timestamp createdAt, Timestamp updatedAt) {
        this.id = id;
        this.activeTenantId = activeTenantId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.isSuperadmin = isSuperadmin;
        this.username = username;
        this.status = status;
        this.lastLoginAt = lastLoginAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getActiveTenantId() { return activeTenantId; }
    public void setActiveTenantId(String activeTenantId) { this.activeTenantId = activeTenantId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public boolean isSuperadmin() { return isSuperadmin; }
    public void setSuperadmin(boolean superadmin) { isSuperadmin = superadmin; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Timestamp lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
