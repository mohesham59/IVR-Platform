package com.nexusivr.ai.model.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * DTMF menu with choices.
 */
public class FlowMenu {
    private final List<FlowChoice> choices = new ArrayList<>();
    private String elseGoto; // fallback target node id
    private int maxAttempts = 3;
    private int timeoutSeconds = 5;

    public List<FlowChoice> getChoices() {
        return choices;
    }

    public void addChoice(FlowChoice choice) {
        if (choice != null) {
            choices.add(choice);
        }
    }

    public String getElseGoto() {
        return elseGoto;
    }

    public void setElseGoto(String elseGoto) {
        this.elseGoto = elseGoto;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getPrimaryLabel() {
        if (!choices.isEmpty()) {
            return choices.get(0).getLabel();
        }
        return "Menu";
    }

    @Override
    public String toString() {
        return "FlowMenu{" +
                "choices=" + choices.size() +
                ", elseGoto='" + elseGoto + '\'' +
                '}';
    }
}
