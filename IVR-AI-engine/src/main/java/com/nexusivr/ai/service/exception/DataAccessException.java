package com.nexusivr.ai.service.exception;

/**
 * Wraps any failure surfaced by a DAO (connectivity, constraint
 * violation, timeout, optimistic-lock conflict). A service never lets
 * a DAO's native exception type escape its boundary; it is always
 * translated into this type first.
 */
public class DataAccessException extends ServiceException {

    public DataAccessException(String daoName, String message, Throwable cause) {
        super("DATA_ACCESS_ERROR", "DAO [" + daoName + "] failed: " + message, cause);
    }
}
