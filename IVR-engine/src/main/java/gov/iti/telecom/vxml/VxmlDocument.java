package gov.iti.telecom.vxml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * VxmlDocument — Object AST representing a VoiceXML 2.1 document.
 */
public class VxmlDocument {
    private String version = "2.1";
    private String xmlNamespace = "http://www.w3.org/2001/vxml";
    private final Map<String, String> properties = new HashMap<>();
    private final Map<String, String> variables = new HashMap<>();
    private final List<VxmlDialog> dialogs = new ArrayList<>();

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getXmlNamespace() {
        return xmlNamespace;
    }

    public void setXmlNamespace(String xmlNamespace) {
        this.xmlNamespace = xmlNamespace;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void addProperty(String name, String value) {
        this.properties.put(name, value);
    }

    public Map<String, String> getVariables() {
        return variables;
    }

    public void addVariable(String name, String expr) {
        this.variables.put(name, expr);
    }

    public List<VxmlDialog> getDialogs() {
        return dialogs;
    }

    public void addDialog(VxmlDialog dialog) {
        this.dialogs.add(dialog);
    }

    public VxmlDialog getDialogById(String id) {
        for (VxmlDialog d : dialogs) {
            if (id.equals(d.getId())) {
                return d;
            }
        }
        return null;
    }
}
