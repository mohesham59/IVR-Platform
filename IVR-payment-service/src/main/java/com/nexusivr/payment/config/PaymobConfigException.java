package com.nexusivr.payment.config;

/**
 * Exception thrown when Paymob configuration fails validation (e.g. missing environment variables).
 */
public class PaymobConfigException extends RuntimeException {

    public PaymobConfigException(String message) {
        super(message);
    }

    public PaymobConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
