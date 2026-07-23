package gov.iti.telecom.vxml;

/**
 * VxmlForm — Represents a VoiceXML <form> dialog.
 */
public class VxmlForm extends VxmlDialog {
    private String prompt;
    private String audioSrc;
    private String fieldName;
    private int fieldLength = 1;
    private String transferDest;
    private String nextTarget;
    private boolean isDisconnect = false;

    public VxmlForm(String id) {
        super(id, "form");
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

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public int getFieldLength() {
        return fieldLength;
    }

    public void setFieldLength(int fieldLength) {
        this.fieldLength = fieldLength;
    }

    public String getTransferDest() {
        return transferDest;
    }

    public void setTransferDest(String transferDest) {
        this.transferDest = transferDest;
    }

    public String getNextTarget() {
        return nextTarget;
    }

    public void setNextTarget(String nextTarget) {
        this.nextTarget = nextTarget;
    }

    public boolean isDisconnect() {
        return isDisconnect;
    }

    public void setDisconnect(boolean disconnect) {
        isDisconnect = disconnect;
    }
}
