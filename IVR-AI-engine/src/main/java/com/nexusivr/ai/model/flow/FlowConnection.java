package com.nexusivr.ai.model.flow;

import java.util.Objects;

/**
 * A connection (edge) between two FlowNodes.
 */
public class FlowConnection {
    private String id;
    private String sourceNodeId;
    private String sourcePort;
    private String targetNodeId;
    private String targetPort;
    private String label;
    private String condition; // optional condition for conditional connections

    public FlowConnection() {
    }

    public FlowConnection(String id, String sourceNodeId, String sourcePort, String targetNodeId, String targetPort) {
        this.id = id;
        this.sourceNodeId = sourceNodeId;
        this.sourcePort = sourcePort;
        this.targetNodeId = targetNodeId;
        this.targetPort = targetPort;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    @Override
    public String toString() {
        return "FlowConnection{" +
                "id='" + id + '\'' +
                ", source='" + sourceNodeId + ':' + sourcePort + '\'' +
                ", target='" + targetNodeId + ':' + targetPort + '\'' +
                '}';
    }
}
