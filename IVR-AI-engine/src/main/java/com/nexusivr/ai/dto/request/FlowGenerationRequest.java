package com.nexusivr.ai.dto.request;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Input for the Flow Generator module: a natural-language description of
 * a desired IVR/chat flow, plus optional constraints. There is no
 * existing-flow input here on purpose — generating from scratch is this
 * DTO's job; editing an existing flow is FlowImprovementRequest's job,
 * kept as a separate request shape because the two have very different
 * required fields (a description vs. a full FlowDto).
 */
public class FlowGenerationRequest {

    private UUID tenantId;
    private String description;
    private String language;
    private Integer maxDepth;
    private List<String> requiredIntents;
    private Map<String, Object> constraints;

    public FlowGenerationRequest() {
        this.requiredIntents = new ArrayList<>();
        this.constraints = new HashMap<>();
    }

    public FlowGenerationRequest(UUID tenantId, String description, String language, Integer maxDepth,
                                  List<String> requiredIntents, Map<String, Object> constraints) {
        this.tenantId = tenantId;
        this.description = description;
        this.language = language;
        this.maxDepth = maxDepth;
        this.requiredIntents = requiredIntents != null ? requiredIntents : new ArrayList<>();
        this.constraints = constraints != null ? constraints : new HashMap<>();
    }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public Integer getMaxDepth() { return maxDepth; }
    public void setMaxDepth(Integer maxDepth) { this.maxDepth = maxDepth; }

    public List<String> getRequiredIntents() { return requiredIntents; }
    public void setRequiredIntents(List<String> requiredIntents) { this.requiredIntents = requiredIntents; }

    public Map<String, Object> getConstraints() { return constraints; }
    public void setConstraints(Map<String, Object> constraints) { this.constraints = constraints; }

    @Override
    public String toString() {
        return "FlowGenerationRequest{" +
                "tenantId=" + tenantId +
                ", description='" + description + '\'' +
                ", language='" + language + '\'' +
                ", maxDepth=" + maxDepth +
                ", requiredIntents=" + requiredIntents +
                ", constraints=" + constraints +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FlowGenerationRequest)) return false;
        FlowGenerationRequest that = (FlowGenerationRequest) o;
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(description, that.description) &&
                Objects.equals(language, that.language) && Objects.equals(maxDepth, that.maxDepth) &&
                Objects.equals(requiredIntents, that.requiredIntents) && Objects.equals(constraints, that.constraints);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, description, language, maxDepth, requiredIntents, constraints);
    }
}
