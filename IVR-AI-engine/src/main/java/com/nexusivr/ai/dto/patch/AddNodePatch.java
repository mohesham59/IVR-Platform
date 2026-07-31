package com.nexusivr.ai.dto.patch;

import java.util.Objects;

/**
 * Adds a new node to the flow.
 */
public class AddNodePatch extends FlowPatchOperation {
    private String newNodeId;
    private String nodeType;
    private String title;
    private String promptText;
    private String targetNodeId;
    private String sourcePort;

    public AddNodePatch() {
    }

    public AddNodePatch(String newNodeId, String nodeType, String title, String promptText, String targetNodeId, String sourcePort) {
        super(newNodeId, "Add node " + newNodeId + " of type " + nodeType);
        this.newNodeId = newNodeId;
        this.nodeType = nodeType;
        this.title = title;
        this.promptText = promptText;
        this.targetNodeId = targetNodeId;
        this.sourcePort = sourcePort;
    }

    @Override
    public String getType() {
        return "ADD_NODE";
    }

    public String getNewNodeId() {
        return newNodeId;
    }

    public void setNewNodeId(String newNodeId) {
        this.newNodeId = newNodeId;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPromptText() {
        return promptText;
    }

    public void setPromptText(String promptText) {
        this.promptText = promptText;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public void setTargetNodeId(String targetNodeId) {
        this.targetNodeId = targetNodeId;
    }

    public String getSourcePort() {
        return sourcePort;
    }

    public void setSourcePort(String sourcePort) {
        this.sourcePort = sourcePort;
    }
}
