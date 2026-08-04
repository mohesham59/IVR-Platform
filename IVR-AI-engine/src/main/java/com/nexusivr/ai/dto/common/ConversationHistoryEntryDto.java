package com.nexusivr.ai.dto.common;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * API-facing view of one conversation_history row. sessionId is nullable
 * here for the same reason it's nullable on the model class: the source
 * ai_sessions row may have been deleted (ON DELETE SET NULL), but the
 * distilled memory is retained. Mirrors the ConversationHistory model
 * closely, but this is the layer that's actually safe to hand to a
 * frontend — no framework/persistence concerns leak through.
 */
public class ConversationHistoryEntryDto {

    private UUID id;
    private UUID sessionId;
    private String customerIdentifier;
    private String summaryText;
    private Instant createdAt;

    public ConversationHistoryEntryDto() {
    }

    public ConversationHistoryEntryDto(UUID id, UUID sessionId, String customerIdentifier,
                                        String summaryText, Instant createdAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.customerIdentifier = customerIdentifier;
        this.summaryText = summaryText;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public String getCustomerIdentifier() { return customerIdentifier; }
    public void setCustomerIdentifier(String customerIdentifier) { this.customerIdentifier = customerIdentifier; }

    public String getSummaryText() { return summaryText; }
    public void setSummaryText(String summaryText) { this.summaryText = summaryText; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "ConversationHistoryEntryDto{" +
                "id=" + id +
                ", sessionId=" + sessionId +
                ", customerIdentifier='" + customerIdentifier + '\'' +
                ", summaryText='" + summaryText + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConversationHistoryEntryDto)) return false;
        ConversationHistoryEntryDto that = (ConversationHistoryEntryDto) o;
        return Objects.equals(id, that.id) && Objects.equals(sessionId, that.sessionId) &&
                Objects.equals(customerIdentifier, that.customerIdentifier) &&
                Objects.equals(summaryText, that.summaryText) && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, sessionId, customerIdentifier, summaryText, createdAt);
    }
}
