package com.nexusivr.ai.dto.patch;

import java.util.Objects;

/**
 * Adds, updates, or removes a menu option.
 */
public class ChangeMenuOptionPatch extends FlowPatchOperation {
    private String action; // ADD, UPDATE, REMOVE
    private String dtmf;
    private String label;
    private String targetNodeId;

    public ChangeMenuOptionPatch() {
    }

    public ChangeMenuOptionPatch(String nodeId, String action, String dtmf, String label, String targetNodeId) {
        super(nodeId, action + " menu option " + dtmf + " in " + nodeId);
        this.action = action;
        this.dtmf = dtmf;
        this.label = label;
        this.targetNodeId = targetNodeId;
    }

    @Override
    public String getType() {
        return "CHANGE_MENU_OPTION";
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
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
}
