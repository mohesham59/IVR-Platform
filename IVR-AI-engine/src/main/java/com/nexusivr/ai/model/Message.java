package com.nexusivr.ai.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Plain Java model for a row in {@code ai_messages}.
 * Represents a single message turn in a conversational AI session.
 */
public class Message {

    private UUID id;
    private UUID sessionId;
    private UUID tenantId;
    private int turnNumber;
    private MessageRole role;
    private String content;
    private String modelUsed;
    private Integer tokensInput;
    private Integer tokensOutput;
    private String metadata;
    private Instant createdAt;

    public Message() {
        this.createdAt = Instant.now();
        this.metadata = "{}";
    }

    public Message(UUID id, UUID sessionId, UUID tenantId, int turnNumber, MessageRole role,
                   String content, String modelUsed, Integer tokensInput, Integer tokensOutput,
                   String metadata, Instant createdAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.tenantId = tenantId;
        this.turnNumber = turnNumber;
        this.role = role;
        this.content = content;
        this.modelUsed = modelUsed;
        this.tokensInput = tokensInput;
        this.tokensOutput = tokensOutput;
        this.metadata = metadata != null ? metadata : "{}";
        this.createdAt = createdAt != null ? createdAt : Instant.now();
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

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
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
        if (this == o) return true;
        if (!(o instanceof Message message)) return false;
        return turnNumber == message.turnNumber &&
                Objects.equals(id, message.id) &&
                Objects.equals(sessionId, message.sessionId) &&
                Objects.equals(tenantId, message.tenantId) &&
                role == message.role &&
                Objects.equals(content, message.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, sessionId, tenantId, turnNumber, role, content);
    }

    @Override
    public String toString() {
        return "Message{" +
                "id=" + id +
                ", sessionId=" + sessionId +
                ", tenantId=" + tenantId +
                ", turnNumber=" + turnNumber +
                ", role=" + role +
                ", content='" + content + '\'' +
                ", modelUsed='" + modelUsed + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
