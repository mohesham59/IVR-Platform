package gov.iti.telecom.vxml;

/**
 * VxmlDialog — Base interface for VoiceXML dialogs (<form> or <menu>).
 */
public abstract class VxmlDialog {
    private final String id;
    private final String type;

    public VxmlDialog(String id, String type) {
        this.id = id;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }
}
