package com.nexusivr.ai.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulates retry rules for LLM provider calls.
 * <p>
 * Retry is permitted ONLY for transient infrastructure-level failures:
 * <ul>
 *   <li>HTTP 500 — internal server error</li>
 *   <li>HTTP 502 — bad gateway</li>
 *   <li>HTTP 503 — service unavailable</li>
 *   <li>HTTP 504 — gateway timeout</li>
 *   <li>Network timeouts</li>
 *   <li>Connection errors</li>
 * </ul>
 * <p>
 * Retry is NEVER permitted for:
 * <ul>
 *   <li>HTTP 400 — bad request (client error)</li>
 *   <li>HTTP 401 — authentication failure (fix API key)</li>
 *   <li>HTTP 403 — forbidden (insufficient permissions)</li>
 *   <li>HTTP 404 — not found</li>
 *   <li>HTTP 409 — conflict</li>
 *   <li>HTTP 429 — quota exceeded (wait and retry later, not immediate retry)</li>
 *   <li>Content-level or validation failures</li>
 * </ul>
 * <p>
 * Validation failures must be repaired locally using {@code ModelAutoRepair} —
 * the LLM must never be called again for a validation failure.
 */
public final class RetryPolicy {

    private static final Logger logger = LoggerFactory.getLogger(RetryPolicy.class);

    private RetryPolicy() {
    }

    /**
     * Returns true if the HTTP status code indicates a retryable error.
     * <p>
     * Retryable conditions:
     * <ol>
     *   <li>HTTP 500 — internal server error</li>
     *   <li>HTTP 502 — bad gateway</li>
     *   <li>HTTP 503 — service unavailable</li>
     *   <li>HTTP 504 — gateway timeout</li>
     *   <li>Status code 0 (no HTTP context) with retryable content patterns — network/timeout errors</li>
     * </ol>
     * All other status codes (including 400, 401, 403, 404, 409, 429) are NOT retryable.
     *
     * @param statusCode the HTTP status code (0 if no HTTP context, e.g. network error)
     * @param content    the response content string (may be null)
     * @return true if the error is retryable
     */
    public static boolean isRetryable(int statusCode, String content) {
        if (statusCode >= 500 && statusCode < 600) {
            logger.warn("RetryPolicy: Retry Reason: HTTP {} - Server error. Status={}.", statusCode, statusCode);
            return true;
        }
        if (statusCode == 0) {
            // status 0 = no HTTP context (network/transport-level failure).
            // Still check content to avoid retrying content-level validation errors
            // (invalid XML, invalid JSON, missing node, etc.) that also arrive with status 0.
            boolean retryable = isRetryableNetworkError(content);
            if (retryable) {
                logger.warn("RetryPolicy: Retry Reason: Network/transport error (status 0). Content={}.", content);
            }
            return retryable;
        }
        if (statusCode == 429) {
            logger.warn("RetryPolicy: NOT retryable — HTTP 429 (quota exceeded). Status={}.", statusCode);
        } else if (statusCode == 401) {
            logger.warn("RetryPolicy: NOT retryable — HTTP 401 (authentication failure). Status={}.", statusCode);
        } else if (statusCode == 403) {
            logger.warn("RetryPolicy: NOT retryable — HTTP 403 (forbidden). Status={}.", statusCode);
        } else if (statusCode >= 400) {
            logger.debug("RetryPolicy: HTTP {} is not retryable.", statusCode);
        }
        return false;
    }

    /**
     * Returns true if the {@link AiResponse} indicates a retryable error.
     * Delegates to {@link #isRetryable(int, String)} using the response's
     * status code and content.
     *
     * @param response the AI response to evaluate
     * @return true if the response indicates a retryable error
     */
    public static boolean isRetryable(AiResponse response) {
        if (response == null) {
            return true;
        }
        return isRetryable(response.getStatusCode(), response.getContent());
    }

    /**
     * Returns true if the exception indicates a retryable infrastructure failure.
     * Retryable exception types:
     * <ul>
     *   <li>{@link java.net.http.HttpTimeoutException} — request timed out</li>
     *   <li>{@link java.net.ConnectException} — server unreachable</li>
     *   <li>{@link java.io.IOException} — network-level I/O failure</li>
     *   <li>{@link InterruptedException} — thread interrupted during call</li>
     * </ul>
     *
     * @param e the exception to evaluate
     * @return true if the exception is retryable
     */
    public static boolean isRetryable(Exception e) {
        if (e == null) {
            return false;
        }
        if (e instanceof java.net.http.HttpTimeoutException
                || e instanceof java.net.ConnectException
                || e instanceof java.io.IOException
                || e instanceof InterruptedException) {
            return true;
        }
        if (e.getMessage() != null && isRetryableNetworkError(e.getMessage())) {
            return true;
        }
        return false;
    }

    /**
     * Returns true if the content string matches a retryable network/transport error pattern.
     * Only matches infrastructure-level failure indicators, never content-level
     * validation errors (invalid XML, invalid JSON, missing node, schema failure, etc.).
     *
     * <p>Deliberately avoids broad keywords like "reset" or "eof" alone since content-level
     * errors may coincidentally contain those substrings. Uses more specific compound phrases.
     */
    private static boolean isRetryableNetworkError(String content) {
        if (content == null) {
            return true;
        }
        String lower = content.toLowerCase();
        return lower.contains("timeout")
                || lower.contains("connectexception")
                || lower.contains("offline or unreachable")
                || lower.contains("unreachable")
                || lower.contains("network error")
                || lower.contains("connection refused")
                || lower.contains("connection reset")
                || lower.contains("reset by peer")
                || lower.contains("no bytes received")
                || lower.contains("0 bytes")
                || lower.contains("premature eof")
                || lower.contains("stream was reset")
                || lower.contains("stream closed unexpectedly")
                || lower.contains("ssl handshake")
                || lower.contains("tls handshake")
                || lower.contains("socket closed")
                || lower.contains("no route to host")
                || lower.contains("broken pipe");
    }
}