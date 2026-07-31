package com.nexusivr.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class VxmlIf {
    private final String cond;
    private VxmlGoto goto_;
    private final List<VxmlPrompt> prompts = new ArrayList<>();
    private VxmlTransfer transfer;
    private boolean disconnect;
    private VxmlIf elseBranch;
    private final List<VxmlIf> elseIfs = new ArrayList<>();

    public VxmlIf(String cond) {
        this.cond = cond != null ? cond : "";
    }

    public String getCond() {
        return cond;
    }

    public VxmlGoto getGoto() {
        return goto_;
    }

    public void setGoto(VxmlGoto goto_) {
        this.goto_ = goto_;
    }

    public List<VxmlPrompt> getPrompts() {
        return prompts;
    }

    public void addPrompt(VxmlPrompt prompt) {
        if (prompt != null) prompts.add(prompt);
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

    public VxmlIf getElseBranch() {
        return elseBranch;
    }

    public void setElseBranch(VxmlIf elseBranch) {
        this.elseBranch = elseBranch;
    }

    public List<VxmlIf> getElseIfs() {
        return elseIfs;
    }

    public void addElseIf(VxmlIf elseIf) {
        if (elseIf != null) elseIfs.add(elseIf);
    }

    public String getGotoTarget() {
        if (goto_ != null && goto_.getNext() != null) {
            String t = goto_.getNext();
            return t.startsWith("#") ? t.substring(1) : t;
        }
        return null;
    }
}
