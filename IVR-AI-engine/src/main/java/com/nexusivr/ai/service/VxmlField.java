package com.nexusivr.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class VxmlField {
    private final String name;
    private final String type;
    private final List<VxmlPrompt> prompts = new ArrayList<>();
    private VxmlGrammar grammar;
    private VxmlBlock filled;
    private VxmlBlock noInput;
    private VxmlBlock noMatch;

    public VxmlField(String name, String type) {
        this.name = name != null ? name : "";
        this.type = type != null ? type : "";
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public List<VxmlPrompt> getPrompts() {
        return prompts;
    }

    public void addPrompt(VxmlPrompt prompt) {
        if (prompt != null) prompts.add(prompt);
    }

    public VxmlGrammar getGrammar() {
        return grammar;
    }

    public void setGrammar(VxmlGrammar grammar) {
        this.grammar = grammar;
    }

    public VxmlBlock getFilled() {
        return filled;
    }

    public void setFilled(VxmlBlock filled) {
        this.filled = filled;
    }

    public VxmlBlock getNoInput() {
        return noInput;
    }

    public void setNoInput(VxmlBlock noInput) {
        this.noInput = noInput;
    }

    public VxmlBlock getNoMatch() {
        return noMatch;
    }

    public void setNoMatch(VxmlBlock noMatch) {
        this.noMatch = noMatch;
    }

    public String getPrimaryPromptText() {
        if (!prompts.isEmpty()) {
            return prompts.get(0).getText();
        }
        return null;
    }

    public List<String> getDtmfOptions() {
        List<String> options = new ArrayList<>();
        if (grammar != null && grammar.getTokens() != null) {
            options.addAll(grammar.getTokens());
        }
        if (type != null && !type.isBlank()) {
            options.add(type);
        }
        return options;
    }
}
