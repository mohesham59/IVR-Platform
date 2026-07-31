package com.nexusivr.ai.dto.patch;

import java.util.Objects;

/**
 * Base class for all flow patch operations.
 */
public abstract class FlowPatchOperation {
    protected String nodeId;
    protected String description;

    public FlowPatchOperation() {
    }

    public FlowPatchOperation(String nodeId, String description) {
        this.nodeId = nodeId;
        this.description = description;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public abstract String getType();

    @Override
    public String toString() {
        return getType() + "{" +
                "nodeId='" + nodeId + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
