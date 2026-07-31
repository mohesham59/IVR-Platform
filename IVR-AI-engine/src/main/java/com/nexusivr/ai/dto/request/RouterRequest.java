package com.nexusivr.ai.dto.request;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Input for the Router module: given where a session currently sits in
 * a flow and what the customer just said/typed, decide the next node.
 * availableRouteIds is the candidate set the router is allowed to choose
 * from — normally the ids of the outgoing FlowEdgeDto.toNodeId values
 * for the current node — passed explicitly rather than re-fetched, so
 * the router stays a pure decision function over caller-supplied state.
 */
public class RouterRequest {

    private UUID tenantId;
    private UUID sessionId;
    private String currentNodeId;
    private String utterance;
    private List<String> availableRouteIds;
    private Map<String, Object> context;

    public RouterRequest() {
        this.availableRouteIds = new ArrayList<>();
        this.context = new HashMap<>();
    }

    public RouterRequest(UUID tenantId, UUID sessionId, String currentNodeId, String utterance,
                          List<String> availableRouteIds, Map<String, Object> context) {
        this.tenantId = tenantId;
        this.sessionId = sessionId;
        this.currentNodeId = currentNodeId;
        this.utterance = utterance;
        this.availableRouteIds = availableRouteIds != null ? availableRouteIds : new ArrayList<>();
        this.context = context != null ? context : new HashMap<>();
    }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

    public String getCurrentNodeId() { return currentNodeId; }
    public void setCurrentNodeId(String currentNodeId) { this.currentNodeId = currentNodeId; }

    public String getUtterance() { return utterance; }
    public void setUtterance(String utterance) { this.utterance = utterance; }

    public List<String> getAvailableRouteIds() { return availableRouteIds; }
    public void setAvailableRouteIds(List<String> availableRouteIds) { this.availableRouteIds = availableRouteIds; }

    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context; }

    @Override
    public String toString() {
        return "RouterRequest{" +
                "tenantId=" + tenantId +
                ", sessionId=" + sessionId +
                ", currentNodeId='" + currentNodeId + '\'' +
                ", utterance='" + utterance + '\'' +
                ", availableRouteIds=" + availableRouteIds +
                ", context=" + context +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RouterRequest)) return false;
        RouterRequest that = (RouterRequest) o;
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(sessionId, that.sessionId) &&
                Objects.equals(currentNodeId, that.currentNodeId) && Objects.equals(utterance, that.utterance) &&
                Objects.equals(availableRouteIds, that.availableRouteIds) && Objects.equals(context, that.context);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, sessionId, currentNodeId, utterance, availableRouteIds, context);
    }
}
