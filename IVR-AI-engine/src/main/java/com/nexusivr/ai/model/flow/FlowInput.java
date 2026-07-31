package com.nexusivr.ai.model.flow;

import java.util.Objects;

/**
 * DTMF input collection.
 */
public class FlowInput {
    private String name;
    private String mode = "dtmf";
    private int digits = 1;
    private String terminator = "#";
    private int timeoutSeconds = 5;
    private int maxAttempts = 3;

    public FlowInput() {
    }

    public FlowInput(String name, int digits) {
        this.name = name;
        this.digits = digits;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public int getDigits() {
        return digits;
    }

    public void setDigits(int digits) {
        this.digits = digits;
    }

    public String getTerminator() {
        return terminator;
    }

    public void setTerminator(String terminator) {
        this.terminator = terminator;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    @Override
    public String toString() {
        return "FlowInput{" +
                "name='" + name + '\'' +
                ", digits=" + digits +
                '}';
    }
}
