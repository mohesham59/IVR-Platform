package com.nexusivr.ai.exception;

/**
 * Exception thrown when a database or persistence error occurs in the DAO layer.
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
