package com.nexusivr.ai.model.flow;

import java.util.Objects;

/**
 * Webhook trigger node.
 */
public class FlowWebhook {
    private String url;
    private String method = "POST";
    private String headers;

    public FlowWebhook() {
    }

    public FlowWebhook(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getHeaders() {
        return headers;
    }

    public void setHeaders(String headers) {
        this.headers = headers;
    }

    @Override
    public String toString() {
        return "FlowWebhook{" +
                "method=" + method +
                ", url='" + url + '\'' +
                '}';
    }
}
