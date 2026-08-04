package com.nexusivr.ai.exception;

/**
 * High-level exception thrown by service layer components when business execution fails.
 */
public class ServiceException extends RuntimeException {

    public ServiceException(String message) {
        super(message);
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
