package com.nexusivr.payment;

import com.google.gson.annotations.SerializedName;

/**
 * Value object representing request payload for Paymob Subscription Plan Creation API.
 */
public class SubscriptionPlanRequest {

    @SerializedName("name")
    private String name;

    @SerializedName("amount_cents")
    private long amountPiasters;

    @SerializedName("currency")
    private String currency;

    @SerializedName("interval")
    private String interval;

    public SubscriptionPlanRequest() {
        this.currency = "EGP";
        this.interval = "MONTHLY";
    }

    public SubscriptionPlanRequest(String name, long amountPiasters, String currency, String interval) {
        this.name = name;
        this.amountPiasters = amountPiasters;
        this.currency = currency != null ? currency : "EGP";
        this.interval = interval != null ? interval : "MONTHLY";
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getAmountPiasters() { return amountPiasters; }
    public void setAmountPiasters(long amountPiasters) { this.amountPiasters = amountPiasters; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getInterval() { return interval; }
    public void setInterval(String interval) { this.interval = interval; }
}
