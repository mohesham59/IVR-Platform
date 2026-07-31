package com.nexusivr.ai.model.flow;

import java.util.Objects;

/**
 * Recording configuration.
 */
public class FlowRecording {
    private String filename;
    private int maxDurationSeconds = 60;
    private String format = "wav";
    private boolean beep = true;

    public FlowRecording() {
    }

    public FlowRecording(String filename) {
        this.filename = filename;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
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

    public boolean isBeep() {
        return beep;
    }

    public void setBeep(boolean beep) {
        this.beep = beep;
    }

    @Override
    public String toString() {
        return "FlowRecording{" +
                "filename='" + filename + '\'' +
                '}';
    }
}
