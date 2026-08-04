package com.nexusivr.ai.service;

/**
 * Thrown when an LLM response cannot be normalized into valid VoiceXML.
 * <p>
 * This exception carries enough context for diagnostics:
 * <ul>
 *   <li>The normalization step that failed</li>
 *   <li>A snippet of the original response</li>
 *   <li>The underlying cause if available</li>
 * </ul>
 */
public class LlmResponseNormalizationException extends RuntimeException {
    public LlmResponseNormalizationException(String message) {
        super(message);
    }

    public LlmResponseNormalizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
