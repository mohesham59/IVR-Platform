package com.nexusivr.ai.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Plain Java model for a row in {@code conversation_history}.
 *
 * Long-term, cross-session memory keyed by {@code customerIdentifier}.
 * {@code sessionId} is nullable — it points back to the {@link AiSession}
 * that produced this memory entry, but the memory itself is designed to
 * outlive any single session (mirrors the {@code SET NULL} delete rule
 * on the underlying foreign key).
 */
public class ConversationHistory {

    private UUID id;
    private UUID tenantId;
    private String customerIdentifier;
    private UUID sessionId;
    private String summaryText;
    private Instant createdAt;

    public ConversationHistory() {
    }

    public ConversationHistory(UUID id,
                                UUID tenantId,
                                String customerIdentifier,
                                UUID sessionId,
                                String summaryText,
                                Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.customerIdentifier = customerIdentifier;
        this.sessionId = sessionId;
        this.summaryText = summaryText;
        this.createdAt = createdAt;
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

    public String getCustomerIdentifier() {
        return customerIdentifier;
    }

    public void setCustomerIdentifier(String customerIdentifier) {
        this.customerIdentifier = customerIdentifier;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConversationHistory)) {
            return false;
        }
        ConversationHistory that = (ConversationHistory) o;
        return Objects.equals(id, that.id)
                && Objects.equals(tenantId, that.tenantId)
                && Objects.equals(customerIdentifier, that.customerIdentifier)
                && Objects.equals(sessionId, that.sessionId)
                && Objects.equals(summaryText, that.summaryText)
                && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId, customerIdentifier, sessionId,
                summaryText, createdAt);
    }

    @Override
    public String toString() {
        return "ConversationHistory{" +
                "id=" + id +
                ", tenantId=" + tenantId +
                ", customerIdentifier='" + customerIdentifier + '\'' +
                ", sessionId=" + sessionId +
                ", summaryText='" + summaryText + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
