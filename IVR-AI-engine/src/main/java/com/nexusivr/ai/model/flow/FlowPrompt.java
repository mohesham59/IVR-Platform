package com.nexusivr.ai.model.flow;

import java.util.Objects;

/**
 * Text-to-speech or audio playback prompt.
 */
public class FlowPrompt {
    private String text;
    private String voice;
    private String language;

    public FlowPrompt() {
    }

    public FlowPrompt(String text) {
        this.text = text;
    }

    public FlowPrompt(String text, String voice, String language) {
        this.text = text;
        this.voice = voice;
        this.language = language;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getVoice() {
        return voice;
    }

    public void setVoice(String voice) {
        this.voice = voice;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getDisplayText() {
        if (text == null || text.isBlank()) return "";
        return text.length() > 60 ? text.substring(0, 57) + "..." : text;
    }

    @Override
    public String toString() {
        return "FlowPrompt{" +
                "text='" + text + '\'' +
                ", voice='" + voice + '\'' +
                '}';
    }
}
