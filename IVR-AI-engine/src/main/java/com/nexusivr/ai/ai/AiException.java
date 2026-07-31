package com.nexusivr.ai.ai;

/**
 * Exception thrown when an error occurs within the AI Integration layer or when calling AI providers.
 * Encapsulates lower-level provider/HTTP errors to avoid exposing provider implementation details.
 */
public class AiException extends RuntimeException {

    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }

    public AiException(Throwable cause) {
        super(cause);
    }
}
