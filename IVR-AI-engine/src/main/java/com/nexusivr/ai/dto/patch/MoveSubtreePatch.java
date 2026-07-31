package com.nexusivr.ai.dto.patch;

import java.util.Objects;

/**
 * Moves a subtree rooted at a node to a new parent.
 */
public class MoveSubtreePatch extends FlowPatchOperation {
    private String newParentNodeId;
    private String newSourcePort;

    public MoveSubtreePatch() {
    }

    public MoveSubtreePatch(String nodeId, String newParentNodeId, String newSourcePort) {
        super(nodeId, "Move subtree rooted at " + nodeId + " to " + newParentNodeId);
        this.newParentNodeId = newParentNodeId;
        this.newSourcePort = newSourcePort;
    }

    @Override
    public String getType() {
        return "MOVE_SUBTREE";
    }

    public String getNewParentNodeId() {
        return newParentNodeId;
    }

    public void setNewParentNodeId(String newParentNodeId) {
        this.newParentNodeId = newParentNodeId;
    }

    public String getNewSourcePort() {
        return newSourcePort;
    }

    public void setNewSourcePort(String newSourcePort) {
        this.newSourcePort = newSourcePort;
    }
}
