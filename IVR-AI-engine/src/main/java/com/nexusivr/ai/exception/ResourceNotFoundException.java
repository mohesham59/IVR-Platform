package com.nexusivr.ai.exception;

/**
 * Exception thrown when a requested domain resource is not found.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
