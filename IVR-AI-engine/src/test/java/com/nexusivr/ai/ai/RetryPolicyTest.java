package com.nexusivr.ai.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyTest {

    @Test
    void notRetryableOnHttp429() {
        assertFalse(RetryPolicy.isRetryable(429, "rate limit exceeded"));
    }

    @Test
    void retryableOnHttp500() {
        assertTrue(RetryPolicy.isRetryable(500, "internal server error"));
    }

    @Test
    void retryableOnHttp502() {
        assertTrue(RetryPolicy.isRetryable(502, "bad gateway"));
    }

    @Test
    void retryableOnHttp503() {
        assertTrue(RetryPolicy.isRetryable(503, "service unavailable"));
    }

    @Test
    void retryableOnHttp504() {
        assertTrue(RetryPolicy.isRetryable(504, "gateway timeout"));
    }

    @Test
    void notRetryableOnHttp400() {
        assertFalse(RetryPolicy.isRetryable(400, "bad request"));
    }

    @Test
    void notRetryableOnHttp401() {
        assertFalse(RetryPolicy.isRetryable(401, "unauthorized"));
    }

    @Test
    void notRetryableOnHttp403() {
        assertFalse(RetryPolicy.isRetryable(403, "forbidden"));
    }

    @Test
    void notRetryableOnHttp404() {
        assertFalse(RetryPolicy.isRetryable(404, "not found"));
    }

    @Test
    void notRetryableOnHttp422() {
        assertFalse(RetryPolicy.isRetryable(422, "unprocessable entity"));
    }

    @Test
    void retryableOnTimeoutContent() {
        assertTrue(RetryPolicy.isRetryable(0, "request timeout after 30s"));
    }

    @Test
    void retryableOnConnectionErrorContent() {
        assertTrue(RetryPolicy.isRetryable(0, "Connection refused"));
    }

    @Test
    void retryableOnUnreachableContent() {
        assertTrue(RetryPolicy.isRetryable(0, "server offline or unreachable"));
    }

    @Test
    void retryableOnNetworkErrorContent() {
        assertTrue(RetryPolicy.isRetryable(0, "Network error communicating with service"));
    }

    @Test
    void notRetryableOnInvalidXmlContent() {
        assertFalse(RetryPolicy.isRetryable(0, "Invalid XML: unexpected token"));
    }

    @Test
    void notRetryableOnInvalidJsonContent() {
        assertFalse(RetryPolicy.isRetryable(0, "Invalid JSON: unexpected character"));
    }

    @Test
    void notRetryableOnUnsupportedNodeType() {
        assertFalse(RetryPolicy.isRetryable(0, "Unsupported node type: unknown_node"));
    }

    @Test
    void notRetryableOnMissingNode() {
        assertFalse(RetryPolicy.isRetryable(0, "Missing required node: start"));
    }

    @Test
    void notRetryableOnMissingEdge() {
        assertFalse(RetryPolicy.isRetryable(0, "Missing edge for node n1"));
    }

    @Test
    void notRetryableOnInvalidFlowStructure() {
        assertFalse(RetryPolicy.isRetryable(0, "Invalid flow structure: no end node"));
    }

    @Test
    void notRetryableOnSchemaValidationFailure() {
        assertFalse(RetryPolicy.isRetryable(0, "Schema validation failed: missing required field"));
    }

    @Test
    void retryableOnNullContent() {
        assertTrue(RetryPolicy.isRetryable(0, null));
    }

    @Test
    void retryableOnNullResponse() {
        assertTrue(RetryPolicy.isRetryable((AiResponse) null));
    }

    @Test
    void notRetryableOnSuccessfulResponse() {
        AiResponse success = new AiResponse("valid content", "model", 10, 20, false, null, null, 200);
        assertFalse(RetryPolicy.isRetryable(success));
    }

    @Test
    void notRetryableOn429Response() {
        AiResponse rateLimited = new AiResponse("quota exceeded", "model", 0, 0, true, null, null, 429);
        assertFalse(RetryPolicy.isRetryable(rateLimited));
    }

    @Test
    void notRetryableOn401Response() {
        AiResponse authError = new AiResponse("unauthorized", "model", 0, 0, true, null, null, 401);
        assertFalse(RetryPolicy.isRetryable(authError));
    }

    @Test
    void notRetryableOn403Response() {
        AiResponse forbidden = new AiResponse("forbidden", "model", 0, 0, true, null, null, 403);
        assertFalse(RetryPolicy.isRetryable(forbidden));
    }

    @Test
    void notRetryableOn404Response() {
        AiResponse notFound = new AiResponse("not found", "model", 0, 0, true, null, null, 404);
        assertFalse(RetryPolicy.isRetryable(notFound));
    }

    @Test
    void retryableOn500Response() {
        AiResponse serverError = new AiResponse("internal error", "model", 0, 0, true, null, null, 500);
        assertTrue(RetryPolicy.isRetryable(serverError));
    }

    @Test
    void retryableOn503Response() {
        AiResponse serviceUnavailable = new AiResponse("service unavailable", "model", 0, 0, true, null, null, 503);
        assertTrue(RetryPolicy.isRetryable(serviceUnavailable));
    }

    @Test
    void retryableOnHttpTimeoutException() {
        assertTrue(RetryPolicy.isRetryable(new java.net.http.HttpTimeoutException("timeout")));
    }

    @Test
    void retryableOnConnectException() {
        assertTrue(RetryPolicy.isRetryable(new java.net.ConnectException("connection refused")));
    }

    @Test
    void retryableOnIOException() {
        assertTrue(RetryPolicy.isRetryable(new java.io.IOException("network error")));
    }

    @Test
    void retryableOnInterruptedException() {
        assertTrue(RetryPolicy.isRetryable(new InterruptedException("thread interrupted")));
    }

    @Test
    void notRetryableOnRuntimeException() {
        assertFalse(RetryPolicy.isRetryable(new RuntimeException("unexpected error")));
    }

    @Test
    void notRetryableOnIllegalArgumentException() {
        assertFalse(RetryPolicy.isRetryable(new IllegalArgumentException("invalid argument")));
    }
}