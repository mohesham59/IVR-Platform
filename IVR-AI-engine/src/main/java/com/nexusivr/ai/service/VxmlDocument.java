package com.nexusivr.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Intermediate model representing a parsed VoiceXML document.
 */
public class VxmlDocument {
    private String version;
    private final List<VxmlForm> forms = new ArrayList<>();

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<VxmlForm> getForms() {
        return forms;
    }

    public void addForm(VxmlForm form) {
        if (form != null) {
            forms.add(form);
        }
    }

    public VxmlForm getForm(String id) {
        return forms.stream()
                .filter(f -> Objects.equals(f.getId(), id))
                .findFirst()
                .orElse(null);
    }
}
