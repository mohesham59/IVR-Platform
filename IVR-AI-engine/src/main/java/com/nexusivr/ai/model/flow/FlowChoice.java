package com.nexusivr.ai.model.flow;

import java.util.Objects;

/**
 * A single choice within a DTMF menu.
 */
public class FlowChoice {
    private String key; // e.g., "key1", "key2", "key0"
    private String dtmf; // raw DTMF digit
    private String label;
    private String targetNodeId; // where this choice goes
    private String targetPort;
    private String accept; // e.g., "digits 1"

    public FlowChoice() {
    }

    public FlowChoice(String key, String label, String targetNodeId) {
        this.key = key;
        this.label = label;
        this.targetNodeId = targetNodeId;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getDtmf() {
        return dtmf;
    }

    public void setDtmf(String dtmf) {
        this.dtmf = dtmf;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
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

    public String getAccept() {
        return accept;
    }

    public void setAccept(String accept) {
        this.accept = accept;
    }

    @Override
    public String toString() {
        return "FlowChoice{" +
                "key='" + key + '\'' +
                ", label='" + label + '\'' +
                ", target='" + targetNodeId + '\'' +
                '}';
    }
}
