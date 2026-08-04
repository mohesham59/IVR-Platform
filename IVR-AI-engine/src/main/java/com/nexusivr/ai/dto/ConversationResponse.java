package com.nexusivr.ai.dto;

import com.nexusivr.ai.model.Message;
import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Object for returning conversation history transcripts.
 */
public class ConversationResponse {

    private UUID sessionId;
    private UUID tenantId;
    private List<Message> messages;
    private long totalMessages;

    public ConversationResponse() {
    }

    public ConversationResponse(UUID sessionId, UUID tenantId, List<Message> messages, long totalMessages) {
        this.sessionId = sessionId;
        this.tenantId = tenantId;
        this.messages = messages;
        this.totalMessages = totalMessages;
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

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public long getTotalMessages() {
        return totalMessages;
    }

    public void setTotalMessages(long totalMessages) {
        this.totalMessages = totalMessages;
    }
}
