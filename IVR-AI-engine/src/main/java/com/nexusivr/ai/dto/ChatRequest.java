package com.nexusivr.ai.dto;

import com.nexusivr.ai.model.Channel;
import java.util.UUID;

/**
 * Data Transfer Object for inbound chat turn requests.
 */
public class ChatRequest {

    private UUID tenantId;
    private UUID sessionId;
    private String customerIdentifier;
    private Channel channel;
    private String userMessage;
    private String flowContext;

    public ChatRequest() {
    }

    public ChatRequest(UUID tenantId, UUID sessionId, String customerIdentifier, Channel channel, String userMessage) {
        this.tenantId = tenantId;
        this.sessionId = sessionId;
        this.customerIdentifier = customerIdentifier;
        this.channel = channel;
        this.userMessage = userMessage;
    }

    public ChatRequest(UUID tenantId, UUID sessionId, String customerIdentifier, Channel channel, String userMessage, String flowContext) {
        this(tenantId, sessionId, customerIdentifier, channel, userMessage);
        this.flowContext = flowContext;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getCustomerIdentifier() {
        return customerIdentifier;
    }

    public void setCustomerIdentifier(String customerIdentifier) {
        this.customerIdentifier = customerIdentifier;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public String getFlowContext() {
        return flowContext;
    }

    public void setFlowContext(String flowContext) {
        this.flowContext = flowContext;
    }
}
