package com.nexusivr.ai.dto.common;

import com.nexusivr.ai.model.MessageRole;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * API-facing view of a single transcript turn. Deliberately reuses
 * com.nexusivr.ai.model.MessageRole (USER/ASSISTANT/SYSTEM) instead of
 * declaring a duplicate DTO-layer enum: role is shared vocabulary between
 * the DB row and the API contract, not an implementation detail worth
 * decoupling, unlike the flow-graph enums which have no table backing
 * them at all in the MVP schema.
 *
 * Excludes sessionId and tenantId on purpose — this DTO is always used
 * nested inside a response that already carries those (ChatResponse,
 * ConversationHistoryResponse), so repeating them on every message would
 * just be redundant payload.
 */
public class MessageDto {

    private UUID id;
    private int turnNumber;
    private MessageRole role;
    private String content;
    private TokenUsageDto tokenUsage;
    private Instant createdAt;

    public MessageDto() {
    }

    public MessageDto(UUID id, int turnNumber, MessageRole role, String content,
                       TokenUsageDto tokenUsage, Instant createdAt) {
        this.id = id;
        this.turnNumber = turnNumber;
        this.role = role;
        this.content = content;
        this.tokenUsage = tokenUsage;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public int getTurnNumber() { return turnNumber; }
    public void setTurnNumber(int turnNumber) { this.turnNumber = turnNumber; }

    public MessageRole getRole() { return role; }
    public void setRole(MessageRole role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public TokenUsageDto getTokenUsage() { return tokenUsage; }
    public void setTokenUsage(TokenUsageDto tokenUsage) { this.tokenUsage = tokenUsage; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "MessageDto{" +
                "id=" + id +
                ", turnNumber=" + turnNumber +
                ", role=" + role +
                ", content='" + content + '\'' +
                ", tokenUsage=" + tokenUsage +
                ", createdAt=" + createdAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MessageDto)) return false;
        MessageDto that = (MessageDto) o;
        return turnNumber == that.turnNumber && Objects.equals(id, that.id) &&
                role == that.role && Objects.equals(content, that.content) &&
                Objects.equals(tokenUsage, that.tokenUsage) && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, turnNumber, role, content, tokenUsage, createdAt);
    }
}
