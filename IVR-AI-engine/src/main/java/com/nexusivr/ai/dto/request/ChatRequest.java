package com.nexusivr.ai.dto.request;

import com.nexusivr.ai.dto.common.FunctionDefinitionDto;
import com.nexusivr.ai.model.Channel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Input for one turn of the AI Chat module. sessionId is nullable: a
 * null value tells the service to start a new ai_sessions row (in which
 * case channel is required); a non-null value continues an existing
 * session and channel/customerIdentifier are ignored if supplied, since
 * they're already fixed on the row. availableFunctions is optional and
 * only relevant when this deployment wants the assistant to be able to
 * call tools mid-conversation (see Function Calling).
 */
public class ChatRequest {

    private UUID tenantId;
    private UUID sessionId;
    private Channel channel;
    private String customerIdentifier;
    private String message;
    private Map<String, Object> context;
    private List<FunctionDefinitionDto> availableFunctions;

    public ChatRequest() {
        this.context = new HashMap<>();
        this.availableFunctions = new ArrayList<>();
    }

    public ChatRequest(UUID tenantId, UUID sessionId, Channel channel, String customerIdentifier,
                        String message, Map<String, Object> context,
                        List<FunctionDefinitionDto> availableFunctions) {
        this.tenantId = tenantId;
        this.sessionId = sessionId;
        this.channel = channel;
        this.customerIdentifier = customerIdentifier;
        this.message = message;
        this.context = context != null ? context : new HashMap<>();
        this.availableFunctions = availableFunctions != null ? availableFunctions : new ArrayList<>();
    }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public Channel getChannel() { return channel; }
    public void setChannel(Channel channel) { this.channel = channel; }

    public String getCustomerIdentifier() { return customerIdentifier; }
    public void setCustomerIdentifier(String customerIdentifier) { this.customerIdentifier = customerIdentifier; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context; }

    public List<FunctionDefinitionDto> getAvailableFunctions() { return availableFunctions; }
    public void setAvailableFunctions(List<FunctionDefinitionDto> availableFunctions) { this.availableFunctions = availableFunctions; }

    @Override
    public String toString() {
        return "ChatRequest{" +
                "tenantId=" + tenantId +
                ", sessionId=" + sessionId +
                ", channel=" + channel +
                ", customerIdentifier='" + customerIdentifier + '\'' +
                ", message='" + message + '\'' +
                ", context=" + context +
                ", availableFunctions=" + availableFunctions +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatRequest)) return false;
        ChatRequest that = (ChatRequest) o;
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(sessionId, that.sessionId) &&
                channel == that.channel && Objects.equals(customerIdentifier, that.customerIdentifier) &&
                Objects.equals(message, that.message) && Objects.equals(context, that.context) &&
                Objects.equals(availableFunctions, that.availableFunctions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, sessionId, channel, customerIdentifier, message, context, availableFunctions);
    }
}
