package com.nexusivr.ai.dto.patch;

import java.util.Objects;

/**
 * Deletes a node from the flow.
 */
public class DeleteNodePatch extends FlowPatchOperation {
    private String nodeIdToDelete;

    public DeleteNodePatch() {
    }

    public DeleteNodePatch(String nodeIdToDelete) {
        super(nodeIdToDelete, "Delete node " + nodeIdToDelete);
        this.nodeIdToDelete = nodeIdToDelete;
    }

    @Override
    public String getType() {
        return "DELETE_NODE";
    }

    public String getNodeIdToDelete() {
        return nodeIdToDelete;
    }

    public void setNodeIdToDelete(String nodeIdToDelete) {
        this.nodeIdToDelete = nodeIdToDelete;
    }
}
