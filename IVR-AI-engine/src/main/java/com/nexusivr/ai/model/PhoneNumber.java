package com.nexusivr.ai.model;

import java.sql.Timestamp;
import java.util.UUID;

public class PhoneNumber {

    private UUID id;
    private UUID tenantId;
    private String phoneNumber;
    private String country;
    private String provider;
    private String assignedFlowId;
    private String assignedFlowName;
    private String status;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public PhoneNumber() {}

    public PhoneNumber(UUID id, UUID tenantId, String phoneNumber, String country, String provider,
                       String assignedFlowId, String assignedFlowName, String status,
                       Timestamp createdAt, Timestamp updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.phoneNumber = phoneNumber;
        this.country = country;
        this.provider = provider;
        this.assignedFlowId = assignedFlowId;
        this.assignedFlowName = assignedFlowName;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getAssignedFlowId() { return assignedFlowId; }
    public void setAssignedFlowId(String assignedFlowId) { this.assignedFlowId = assignedFlowId; }

    public String getAssignedFlowName() { return assignedFlowName; }
    public void setAssignedFlowName(String assignedFlowName) { this.assignedFlowName = assignedFlowName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
