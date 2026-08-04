package com.nexusivr.ai.service;

public class VxmlPrompt {
    private final String text;

    public VxmlPrompt(String text) {
        this.text = text != null ? text : "";
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return text;
    }
}
