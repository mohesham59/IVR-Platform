package com.nexusivr.payment.model;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Model representing a subscription plan.
 * Note: All monetary amounts (pricePiasters) are stored in piasters (EGP * 100) as long integer.
 */
public class SubscriptionPlan {

    private UUID id;
    private String name;
    private long pricePiasters;
    private String billingInterval; // e.g. 'MONTHLY', 'YEARLY'
    private String integrationIds;  // JSON array string of Paymob integration IDs
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public SubscriptionPlan() {
    }

    public SubscriptionPlan(UUID id, String name, long pricePiasters, String billingInterval, String integrationIds) {
        this.id = id;
        this.name = name;
        this.pricePiasters = pricePiasters;
        this.billingInterval = billingInterval;
        this.integrationIds = integrationIds;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getPricePiasters() {
        return pricePiasters;
    }

    public void setPricePiasters(long pricePiasters) {
        this.pricePiasters = pricePiasters;
    }

    public String getBillingInterval() {
        return billingInterval;
    }

    public void setBillingInterval(String billingInterval) {
        this.billingInterval = billingInterval;
    }

    public String getIntegrationIds() {
        return integrationIds;
    }

    public void setIntegrationIds(String integrationIds) {
        this.integrationIds = integrationIds;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "SubscriptionPlan{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", pricePiasters=" + pricePiasters +
                ", billingInterval='" + billingInterval + '\'' +
                ", integrationIds='" + integrationIds + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
