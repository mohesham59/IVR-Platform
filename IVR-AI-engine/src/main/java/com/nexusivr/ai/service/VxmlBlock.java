package com.nexusivr.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class VxmlBlock {
    private final List<VxmlPrompt> prompts = new ArrayList<>();
    private VxmlGoto goto_;
    private VxmlIf if_;
    private VxmlTransfer transfer;
    private boolean disconnect;

    public List<VxmlPrompt> getPrompts() {
        return prompts;
    }

    public void addPrompt(VxmlPrompt prompt) {
        if (prompt != null) prompts.add(prompt);
    }

    public VxmlGoto getGoto() {
        return goto_;
    }

    public void setGoto(VxmlGoto goto_) {
        this.goto_ = goto_;
    }

    public VxmlIf getIf() {
        return if_;
    }

    public void setIf(VxmlIf if_) {
        this.if_ = if_;
    }

    public VxmlTransfer getTransfer() {
        return transfer;
    }

    public void setTransfer(VxmlTransfer transfer) {
        this.transfer = transfer;
    }

    public boolean isDisconnect() {
        return disconnect;
    }

    public void setDisconnect(boolean disconnect) {
        this.disconnect = disconnect;
    }

    public String getPrimaryPromptText() {
        if (!prompts.isEmpty()) {
            return prompts.get(0).getText();
        }
        return null;
    }
}
