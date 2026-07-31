package com.nexusivr.ai.dto.patch;

import java.util.Objects;

/**
 * Renames a node.
 */
public class RenameNodePatch extends FlowPatchOperation {
    private String newTitle;

    public RenameNodePatch() {
    }

    public RenameNodePatch(String nodeId, String newTitle) {
        super(nodeId, "Rename node " + nodeId + " to " + newTitle);
        this.newTitle = newTitle;
    }

    @Override
    public String getType() {
        return "RENAME_NODE";
    }

    public String getNewTitle() {
        return newTitle;
    }

    public void setNewTitle(String newTitle) {
        this.newTitle = newTitle;
    }
}
