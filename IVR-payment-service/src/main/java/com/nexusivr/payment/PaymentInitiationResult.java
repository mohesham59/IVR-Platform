package com.nexusivr.payment;

import java.util.UUID;

/**
 * Result object returned when initiating a payment (subscription or one-time).
 */
public class PaymentInitiationResult {

    private final String checkoutUrl;
    private final UUID transactionId;

    public PaymentInitiationResult(String checkoutUrl, UUID transactionId) {
        this.checkoutUrl = checkoutUrl;
        this.transactionId = transactionId;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public UUID getTransactionId() {
        return transactionId;
    }
}
