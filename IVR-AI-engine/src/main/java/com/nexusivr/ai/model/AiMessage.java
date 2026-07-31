package com.nexusivr.ai.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Plain Java model for a row in {@code ai_messages}.
 *
 * One row per turn in a session's transcript. Holds a foreign key
 * ({@code sessionId}) back to {@link AiSession} rather than an embedded
 * {@code AiSession} reference — this class has no persistence context
 * to lazily resolve a parent through, so the relationship is expressed
 * as a plain id, exactly as it is stored in the database.
 */
public class AiMessage {

    private UUID id;
    private UUID sessionId;
    private UUID tenantId;
    private int turnNumber;
    private MessageRole role;
    private String content;
    private String modelUsed;
    private Integer tokensInput;
    private Integer tokensOutput;
    private Map<String, Object> metadata;
    private Instant createdAt;

    public AiMessage() {
        this.metadata = new HashMap<>();
    }

    public AiMessage(UUID id,
                      UUID sessionId,
                      UUID tenantId,
                      int turnNumber,
                      MessageRole role,
                      String content,
                      String modelUsed,
                      Integer tokensInput,
                      Integer tokensOutput,
                      Map<String, Object> metadata,
                      Instant createdAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.tenantId = tenantId;
        this.turnNumber = turnNumber;
        this.role = role;
        this.content = content;
        this.modelUsed = modelUsed;
        this.tokensInput = tokensInput;
        this.tokensOutput = tokensOutput;
        this.metadata = metadata != null ? metadata : new HashMap<>();
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public int getTurnNumber() {
        return turnNumber;
    }

    public void setTurnNumber(int turnNumber) {
        this.turnNumber = turnNumber;
    }

    public MessageRole getRole() {
        return role;
    }

    public void setRole(MessageRole role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed) {
        this.modelUsed = modelUsed;
    }

    public Integer getTokensInput() {
        return tokensInput;
    }

    public void setTokensInput(Integer tokensInput) {
        this.tokensInput = tokensInput;
    }

    public Integer getTokensOutput() {
        return tokensOutput;
    }

    public void setTokensOutput(Integer tokensOutput) {
        this.tokensOutput = tokensOutput;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AiMessage)) {
            return false;
        }
        AiMessage that = (AiMessage) o;
        return turnNumber == that.turnNumber
                && Objects.equals(id, that.id)
                && Objects.equals(sessionId, that.sessionId)
                && Objects.equals(tenantId, that.tenantId)
                && role == that.role
                && Objects.equals(content, that.content)
                && Objects.equals(modelUsed, that.modelUsed)
                && Objects.equals(tokensInput, that.tokensInput)
                && Objects.equals(tokensOutput, that.tokensOutput)
                && Objects.equals(metadata, that.metadata)
                && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, sessionId, tenantId, turnNumber, role,
                content, modelUsed, tokensInput, tokensOutput, metadata,
                createdAt);
    }

    @Override
    public String toString() {
        return "AiMessage{" +
                "id=" + id +
                ", sessionId=" + sessionId +
                ", tenantId=" + tenantId +
                ", turnNumber=" + turnNumber +
                ", role=" + role +
                ", content='" + content + '\'' +
                ", modelUsed='" + modelUsed + '\'' +
                ", tokensInput=" + tokensInput +
                ", tokensOutput=" + tokensOutput +
                ", metadata=" + metadata +
                ", createdAt=" + createdAt +
                '}';
    }
}
