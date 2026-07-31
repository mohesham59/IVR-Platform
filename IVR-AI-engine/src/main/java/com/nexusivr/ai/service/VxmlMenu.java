package com.nexusivr.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class VxmlMenu {
    private final String id;
    private final List<VxmlPrompt> prompts = new ArrayList<>();
    private final List<VxmlChoice> choices = new ArrayList<>();
    private VxmlGoto elseGoto;

    public VxmlMenu(String id) {
        this.id = id != null ? id : "";
    }

    public String getId() {
        return id;
    }

    public List<VxmlPrompt> getPrompts() {
        return prompts;
    }

    public void addPrompt(VxmlPrompt prompt) {
        if (prompt != null) prompts.add(prompt);
    }

    public List<VxmlChoice> getChoices() {
        return choices;
    }

    public void addChoice(VxmlChoice choice) {
        if (choice != null) choices.add(choice);
    }

    public VxmlGoto getElseGoto() {
        return elseGoto;
    }

    public void setElseGoto(VxmlGoto elseGoto) {
        this.elseGoto = elseGoto;
    }

    public String getPrimaryPromptText() {
        if (!prompts.isEmpty()) {
            return prompts.get(0).getText();
        }
        return null;
    }
}
