package com.nexusivr.ai.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Plain Java model for a row in {@code ai_sessions}.
 *
 * The root aggregate of the conversational graph: exactly one row per
 * voice call, chat thread, or WhatsApp conversation. {@link AiMessage}
 * and {@link ConversationHistory} rows reference this class by
 * {@code sessionId}.
 *
 * This is a plain data holder — it has no knowledge of persistence,
 * validation rules, or business logic. Those concerns live in the
 * {@code service} / {@code validator} packages, not here.
 */
public class AiSession {

    private UUID id;
    private UUID tenantId;
    private Channel channel;
    private String externalReferenceId;
    private String customerIdentifier;
    private SessionStatus status;
    private Instant startedAt;
    private Instant endedAt;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Instant updatedAt;

    public AiSession() {
        this.metadata = new HashMap<>();
    }

    public AiSession(UUID id,
                      UUID tenantId,
                      Channel channel,
                      String externalReferenceId,
                      String customerIdentifier,
                      SessionStatus status,
                      Instant startedAt,
                      Instant endedAt,
                      Map<String, Object> metadata,
                      Instant createdAt,
                      Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.channel = channel;
        this.externalReferenceId = externalReferenceId;
        this.customerIdentifier = customerIdentifier;
        this.status = status;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.metadata = metadata != null ? metadata : new HashMap<>();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public String getExternalReferenceId() {
        return externalReferenceId;
    }

    public void setExternalReferenceId(String externalReferenceId) {
        this.externalReferenceId = externalReferenceId;
    }

    public String getCustomerIdentifier() {
        return customerIdentifier;
    }

    public void setCustomerIdentifier(String customerIdentifier) {
        this.customerIdentifier = customerIdentifier;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AiSession)) {
            return false;
        }
        AiSession that = (AiSession) o;
        return Objects.equals(id, that.id)
                && Objects.equals(tenantId, that.tenantId)
                && channel == that.channel
                && Objects.equals(externalReferenceId, that.externalReferenceId)
                && Objects.equals(customerIdentifier, that.customerIdentifier)
                && status == that.status
                && Objects.equals(startedAt, that.startedAt)
                && Objects.equals(endedAt, that.endedAt)
                && Objects.equals(metadata, that.metadata)
                && Objects.equals(createdAt, that.createdAt)
                && Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId, channel, externalReferenceId,
                customerIdentifier, status, startedAt, endedAt, metadata,
                createdAt, updatedAt);
    }

    @Override
    public String toString() {
        return "AiSession{" +
                "id=" + id +
                ", tenantId=" + tenantId +
                ", channel=" + channel +
                ", externalReferenceId='" + externalReferenceId + '\'' +
                ", customerIdentifier='" + customerIdentifier + '\'' +
                ", status=" + status +
                ", startedAt=" + startedAt +
                ", endedAt=" + endedAt +
                ", metadata=" + metadata +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
