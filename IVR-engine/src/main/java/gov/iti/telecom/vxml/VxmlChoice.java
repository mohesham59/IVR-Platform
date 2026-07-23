package gov.iti.telecom.vxml;

/**
 * VxmlChoice — Represents a <choice> tag inside a VoiceXML <menu>.
 */
public class VxmlChoice {
    private final String dtmf;
    private final String next;
    private final String label;

    public VxmlChoice(String dtmf, String next, String label) {
        this.dtmf = dtmf;
        this.next = next;
        this.label = label;
    }

    public String getDtmf() {
        return dtmf;
    }

    public String getNext() {
        return next;
    }

    public String getLabel() {
        return label;
    }
}
