package com.nexusivr.ai.model.flow;

import java.util.Objects;

/**
 * Voicemail collection node.
 */
public class FlowVoicemail {
    private String email;
    private int maxDurationSeconds = 120;
    private String format = "wav";

    public FlowVoicemail() {
    }

    public FlowVoicemail(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public int getMaxDurationSeconds() {
        return maxDurationSeconds;
    }

    public void setMaxDurationSeconds(int maxDurationSeconds) {
        this.maxDurationSeconds = maxDurationSeconds;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    @Override
    public String toString() {
        return "FlowVoicemail{" +
                "email='" + email + '\'' +
                '}';
    }
}
