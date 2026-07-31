package com.nexusivr.ai.model.flow;

import java.util.Objects;

/**
 * A branch within a conditional node (elseif/else).
 */
public class FlowConditionBranch {
    private String condition;
    private String targetNodeId;

    public FlowConditionBranch() {
    }

    public FlowConditionBranch(String condition, String targetNodeId) {
        this.condition = condition;
        this.targetNodeId = targetNodeId;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public void setTargetNodeId(String targetNodeId) {
        this.targetNodeId = targetNodeId;
    }
}
