package com.nexusivr.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class VxmlGrammar {
    private final String mode;
    private final String version;
    private final List<String> tokens;

    public VxmlGrammar(String mode, String version, List<String> tokens) {
        this.mode = mode != null ? mode : "";
        this.version = version != null ? version : "";
        this.tokens = tokens != null ? tokens : new ArrayList<>();
    }

    public String getMode() {
        return mode;
    }

    public String getVersion() {
        return version;
    }

    public List<String> getTokens() {
        return tokens;
    }
}
