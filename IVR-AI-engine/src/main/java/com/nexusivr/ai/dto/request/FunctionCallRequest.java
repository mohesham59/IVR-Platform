package com.nexusivr.ai.dto.request;

import com.nexusivr.ai.dto.common.FunctionDefinitionDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Input for the Function Calling module used standalone (outside a full
 * Chat turn) — e.g. a caller that just wants "given this utterance and
 * this tool catalog, which function(s) apply?" without persisting a
 * message or advancing a session. ChatRequest also carries
 * availableFunctions for the common case where tool use happens inline
 * as part of a normal chat turn; this DTO exists for callers who want
 * function-selection as an isolated capability.
 */
public class FunctionCallRequest {

    private UUID tenantId;
    private UUID sessionId;
    private String userMessage;
    private List<FunctionDefinitionDto> availableFunctions;

    public FunctionCallRequest() {
        this.availableFunctions = new ArrayList<>();
    }

    public FunctionCallRequest(UUID tenantId, UUID sessionId, String userMessage,
                                List<FunctionDefinitionDto> availableFunctions) {
        this.tenantId = tenantId;
        this.sessionId = sessionId;
        this.userMessage = userMessage;
        this.availableFunctions = availableFunctions != null ? availableFunctions : new ArrayList<>();
    }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public String getUserMessage() { return userMessage; }
    public void setUserMessage(String userMessage) { this.userMessage = userMessage; }

    public List<FunctionDefinitionDto> getAvailableFunctions() { return availableFunctions; }
    public void setAvailableFunctions(List<FunctionDefinitionDto> availableFunctions) { this.availableFunctions = availableFunctions; }

    @Override
    public String toString() {
        return "FunctionCallRequest{" +
                "tenantId=" + tenantId +
                ", sessionId=" + sessionId +
                ", userMessage='" + userMessage + '\'' +
                ", availableFunctions=" + availableFunctions +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FunctionCallRequest)) return false;
        FunctionCallRequest that = (FunctionCallRequest) o;
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(sessionId, that.sessionId) &&
                Objects.equals(userMessage, that.userMessage) && Objects.equals(availableFunctions, that.availableFunctions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, sessionId, userMessage, availableFunctions);
    }
}
