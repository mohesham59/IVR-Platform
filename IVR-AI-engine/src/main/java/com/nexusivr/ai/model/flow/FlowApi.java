package com.nexusivr.ai.model.flow;

import java.util.Objects;

/**
 * HTTP API call node.
 */
public class FlowApi {
    private String url;
    private String method = "GET";
    private String headers;
    private String body;
    private int timeoutSeconds = 10;

    public FlowApi() {
    }

    public FlowApi(String url, String method) {
        this.url = url;
        this.method = method;
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

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String toString() {
        return "FlowApi{" +
                "method=" + method +
                ", url='" + url + '\'' +
                '}';
    }
}
