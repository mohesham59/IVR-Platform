package com.nexusivr.ai.service;

public class VxmlGoto {
    private final String next;
    private final String expr;

    public VxmlGoto(String next, String expr) {
        this.next = next;
        this.expr = expr;
    }

    public String getNext() {
        return next;
    }

    public String getExpr() {
        return expr;
    }
}
