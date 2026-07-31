package com.nexusivr.ai.dto.patch;

import java.util.Objects;

/**
 * Updates the prompt text of a node.
 */
public class UpdatePromptPatch extends FlowPatchOperation {
    private String newPromptText;

    public UpdatePromptPatch() {
    }

    public UpdatePromptPatch(String nodeId, String newPromptText) {
        super(nodeId, "Update prompt on " + nodeId);
        this.newPromptText = newPromptText;
    }

    @Override
    public String getType() {
        return "UPDATE_PROMPT";
    }

    public String getNewPromptText() {
        return newPromptText;
    }

    public void setNewPromptText(String newPromptText) {
        this.newPromptText = newPromptText;
    }
}
