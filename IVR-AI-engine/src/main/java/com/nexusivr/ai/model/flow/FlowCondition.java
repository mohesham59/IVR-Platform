package com.nexusivr.ai.model.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Conditional branching node.
 */
public class FlowCondition {
    private String expression;
    private String trueTargetNodeId;
    private String falseTargetNodeId;
    private final List<FlowConditionBranch> branches = new ArrayList<>();

    public FlowCondition() {
    }

    public FlowCondition(String expression) {
        this.expression = expression;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public String getTrueTargetNodeId() {
        return trueTargetNodeId;
    }

    public void setTrueTargetNodeId(String trueTargetNodeId) {
        this.trueTargetNodeId = trueTargetNodeId;
    }

    public String getFalseTargetNodeId() {
        return falseTargetNodeId;
    }

    public void setFalseTargetNodeId(String falseTargetNodeId) {
        this.falseTargetNodeId = falseTargetNodeId;
    }

    public List<FlowConditionBranch> getBranches() {
        return branches;
    }

    public void addBranch(FlowConditionBranch branch) {
        if (branch != null) {
            branches.add(branch);
        }
    }

    @Override
    public String toString() {
        return "FlowCondition{" +
                "expression='" + expression + '\'' +
                ", trueTarget='" + trueTargetNodeId + '\'' +
                ", falseTarget='" + falseTargetNodeId + '\'' +
                '}';
    }
}
