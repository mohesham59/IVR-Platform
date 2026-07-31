package com.nexusivr.ai.service.exception;

/**
 * Base unchecked exception for the entire AI module service layer.
 * Every exception that leaves a service method (interface or impl) is
 * this type or a subtype of it, so the caller (a controller, never a
 * Servlet accessed by the service itself) has one exception family to
 * translate into a transport-level response.
 */
public class ServiceException extends RuntimeException {

    private final String errorCode;

    public ServiceException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ServiceException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
