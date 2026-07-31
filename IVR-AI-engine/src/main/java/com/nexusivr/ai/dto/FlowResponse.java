package com.nexusivr.ai.dto;

import java.util.UUID;

/**
 * Data Transfer Object for IVR Flow responses.
 */
public class FlowResponse {

    private UUID flowId;
    private UUID tenantId;
    private String name;
    private String description;
    private String flowJson;
    private String status;

    public FlowResponse() {
    }

    public FlowResponse(UUID flowId, UUID tenantId, String name, String description, String flowJson, String status) {
        this.flowId = flowId;
        this.tenantId = tenantId;
        this.name = name;
        this.description = description;
        this.flowJson = flowJson;
        this.status = status;
    }

    public UUID getFlowId() {
        return flowId;
    }

    public void setFlowId(UUID flowId) {
        this.flowId = flowId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFlowJson() {
        return flowJson;
    }

    public void setFlowJson(String flowJson) {
        this.flowJson = flowJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
