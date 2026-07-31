package com.nexusivr.ai.dto.patch;

import java.util.Objects;

/**
 * Adds a new edge to the flow.
 */
public class AddEdgePatch extends FlowPatchOperation {
    private String edgeId;
    private String sourceNodeId;
    private String sourcePort;
    private String targetNodeId;
    private String targetPort;

    public AddEdgePatch() {
    }

    public AddEdgePatch(String edgeId, String sourceNodeId, String sourcePort, String targetNodeId, String targetPort) {
        super(sourceNodeId, "Add edge from " + sourceNodeId + " to " + targetNodeId);
        this.edgeId = edgeId;
        this.sourceNodeId = sourceNodeId;
        this.sourcePort = sourcePort;
        this.targetNodeId = targetNodeId;
        this.targetPort = targetPort;
    }

    @Override
    public String getType() {
        return "ADD_EDGE";
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

    public String getSourcePort() {
        return sourcePort;
    }

    public void setSourcePort(String sourcePort) {
        this.sourcePort = sourcePort;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public void setTargetNodeId(String targetNodeId) {
        this.targetNodeId = targetNodeId;
    }

    public String getTargetPort() {
        return targetPort;
    }

    public void setTargetPort(String targetPort) {
        this.targetPort = targetPort;
    }
}
