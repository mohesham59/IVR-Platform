package com.nexusivr.ai.dto.patch;

import java.util.Objects;

/**
 * Deletes an edge from the flow.
 */
public class DeleteEdgePatch extends FlowPatchOperation {
    private String edgeId;
    private String sourceNodeId;
    private String targetNodeId;

    public DeleteEdgePatch() {
    }

    public DeleteEdgePatch(String edgeId, String sourceNodeId, String targetNodeId) {
        super(sourceNodeId, "Delete edge " + edgeId);
        this.edgeId = edgeId;
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
    }

    @Override
    public String getType() {
        return "DELETE_EDGE";
    }

    public String getEdgeId() {
        return edgeId;
    }

    public void setEdgeId(String edgeId) {
        this.edgeId = edgeId;
    }

    public String getSourceNodeId() {
        return sourceNodeId;
    }

    public void setSourceNodeId(String sourceNodeId) {
        this.sourceNodeId = sourceNodeId;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public void setTargetNodeId(String targetNodeId) {
        this.targetNodeId = targetNodeId;
    }
}
