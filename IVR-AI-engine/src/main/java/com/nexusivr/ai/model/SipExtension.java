package com.nexusivr.ai.model;

import java.sql.Timestamp;
import java.util.UUID;

public class SipExtension {

    private UUID id;
    private UUID tenantId;
    private String extensionNumber;
    private String displayName;
    private UUID assignedUserId;
    private String sipPassword;
    private boolean tlsEnabled;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Transient live status fields queried from Asterisk
    private String registrationStatus = "Offline"; // "Registered", "Registering", "Offline"
    private String callStatus = "Idle";             // "Idle", "In Call"
    private int liveChannels = 0;

    public SipExtension() {}

    public SipExtension(UUID id, UUID tenantId, String extensionNumber, String displayName,
                        UUID assignedUserId, String sipPassword, boolean tlsEnabled,
                        Timestamp createdAt, Timestamp updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.extensionNumber = extensionNumber;
        this.displayName = displayName;
        this.assignedUserId = assignedUserId;
        this.sipPassword = sipPassword;
        this.tlsEnabled = tlsEnabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getExtensionNumber() { return extensionNumber; }
    public void setExtensionNumber(String extensionNumber) { this.extensionNumber = extensionNumber; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public UUID getAssignedUserId() { return assignedUserId; }
    public void setAssignedUserId(UUID assignedUserId) { this.assignedUserId = assignedUserId; }

    public String getSipPassword() { return sipPassword; }
    public void setSipPassword(String sipPassword) { this.sipPassword = sipPassword; }

    public boolean isTlsEnabled() { return tlsEnabled; }
    public void setTlsEnabled(boolean tlsEnabled) { this.tlsEnabled = tlsEnabled; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public String getRegistrationStatus() { return registrationStatus; }
    public void setRegistrationStatus(String registrationStatus) { this.registrationStatus = registrationStatus; }

    public String getCallStatus() { return callStatus; }
    public void setCallStatus(String callStatus) { this.callStatus = callStatus; }

    public int getLiveChannels() { return liveChannels; }
    public void setLiveChannels(int liveChannels) { this.liveChannels = liveChannels; }
}
