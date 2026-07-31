package com.nexusivr.ai.dto.request;

import com.nexusivr.ai.dto.common.FlowDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Input for the Flow Improvement module: an existing FlowDto (typically
 * one previously returned by Flow Generator, possibly hand-edited since)
 * plus goals to optimize for. analyticsContext is optional free-form
 * data — e.g. per-node drop-off rates from an Analytics query — that lets
 * the improvement be evidence-driven rather than purely stylistic; it is
 * a Map rather than a typed DTO because there is no analytics table yet
 * to define a fixed shape for this data (see AnalyticsMetricDto's note).
 */
public class FlowImprovementRequest {

    private UUID tenantId;
    private FlowDto existingFlow;
    private List<String> improvementGoals;
    private Map<String, Object> analyticsContext;

    public FlowImprovementRequest() {
        this.improvementGoals = new ArrayList<>();
        this.analyticsContext = new HashMap<>();
    }

    public FlowImprovementRequest(UUID tenantId, FlowDto existingFlow, List<String> improvementGoals,
                                   Map<String, Object> analyticsContext) {
        this.tenantId = tenantId;
        this.existingFlow = existingFlow;
        this.improvementGoals = improvementGoals != null ? improvementGoals : new ArrayList<>();
        this.analyticsContext = analyticsContext != null ? analyticsContext : new HashMap<>();
    }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public FlowDto getExistingFlow() { return existingFlow; }
    public void setExistingFlow(FlowDto existingFlow) { this.existingFlow = existingFlow; }

    public List<String> getImprovementGoals() { return improvementGoals; }
    public void setImprovementGoals(List<String> improvementGoals) { this.improvementGoals = improvementGoals; }

    public Map<String, Object> getAnalyticsContext() { return analyticsContext; }
    public void setAnalyticsContext(Map<String, Object> analyticsContext) { this.analyticsContext = analyticsContext; }

    @Override
    public String toString() {
        return "FlowImprovementRequest{" +
                "tenantId=" + tenantId +
                ", existingFlow=" + existingFlow +
                ", improvementGoals=" + improvementGoals +
                ", analyticsContext=" + analyticsContext +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FlowImprovementRequest)) return false;
        FlowImprovementRequest that = (FlowImprovementRequest) o;
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(existingFlow, that.existingFlow) &&
                Objects.equals(improvementGoals, that.improvementGoals) &&
                Objects.equals(analyticsContext, that.analyticsContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, existingFlow, improvementGoals, analyticsContext);
    }
}
