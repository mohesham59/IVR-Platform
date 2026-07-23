package gov.iti.telecom.vxml;

import java.util.ArrayList;
import java.util.List;

/**
 * VxmlMenu — Represents a VoiceXML <menu> dialog.
 */
public class VxmlMenu extends VxmlDialog {
    private String prompt;
    private String audioSrc;
    private final List<VxmlChoice> choices = new ArrayList<>();

    public VxmlMenu(String id) {
        super(id, "menu");
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getAudioSrc() {
        return audioSrc;
    }

    public void setAudioSrc(String audioSrc) {
        this.audioSrc = audioSrc;
    }

    public List<VxmlChoice> getChoices() {
        return choices;
    }

    public void addChoice(VxmlChoice choice) {
        this.choices.add(choice);
    }
}
