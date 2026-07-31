package com.nexusivr.ai.service;

public class VxmlChoice {
    private final String next;
    private final String accept;
    private final String dtmf;
    private final String value;
    private final String text;

    public VxmlChoice(String next, String accept, String dtmf, String value, String text) {
        this.next = next;
        this.accept = accept;
        this.dtmf = dtmf;
        this.value = value;
        this.text = text;
    }

    public String getNext() {
        if (next != null && !next.isBlank()) {
            return next.startsWith("#") ? next.substring(1) : next;
        }
        return null;
    }

    public String getAccept() {
        return accept;
    }

    public String getDtmf() {
        return dtmf;
    }

    public String getValue() {
        return value;
    }

    public String getText() {
        return text;
    }

    public String getKey() {
        if (dtmf != null && !dtmf.isBlank()) {
            return "key" + dtmf.trim();
        }
        if (value != null && !value.isBlank()) {
            return "key" + value.trim();
        }
        if (accept != null && !accept.isBlank()) {
            return "key" + accept.trim();
        }
        return "key1";
    }
}
