package com.nexusivr.ai.service.exception;

/**
 * Thrown when an AI generation request is cancelled by the client.
 */
public class GenerationCancelledException extends ServiceException {

    public GenerationCancelledException(String message) {
        super("GENERATION_CANCELLED", message);
    }
}
