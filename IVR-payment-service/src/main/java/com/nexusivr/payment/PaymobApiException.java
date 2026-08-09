package com.nexusivr.payment;

/**
 * Exception thrown when a Paymob API request returns a non-2xx HTTP status code.
 */
public class PaymobApiException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public PaymobApiException(int statusCode, String responseBody) {
        super("Paymob API request failed with HTTP " + statusCode + ": " + responseBody);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public PaymobApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.responseBody = null;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
