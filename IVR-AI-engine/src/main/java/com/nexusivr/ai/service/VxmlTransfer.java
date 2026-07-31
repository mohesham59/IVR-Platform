package com.nexusivr.ai.service;

public class VxmlTransfer {
    private final String dest;

    public VxmlTransfer(String dest) {
        this.dest = dest != null ? dest : "";
    }

    public String getDest() {
        return dest;
    }
}
