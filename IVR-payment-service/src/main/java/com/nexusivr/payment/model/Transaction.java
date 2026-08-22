package com.nexusivr.payment.model;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Model representing a payment transaction (subscription payment or one-time payment).
 * Note: All monetary amounts (amountPiasters) are stored in piasters (EGP * 100) as long integer.
 */
public class Transaction {

    private UUID id;
    private UUID tenantId;
    private String type; // 'SUBSCRIPTION', 'ONE_TIME'
    private long amountPiasters;
    private String currency = "EGP";
    private String status; // 'PENDING', 'SUCCESS', 'FAILED'
    private String paymobTransactionId;
    private String paymobOrderId;
    private UUID planId;
    private String cardToken;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    /** Transient: populated by JOIN against tenants table for superadmin views. Not persisted. */
    private String tenantDisplayName;

    public Transaction() {
    }

    public Transaction(UUID id, UUID tenantId, String type, long amountPiasters, String currency, String status) {
        this.id = id;
        this.tenantId = tenantId;
        this.type = type;
        this.amountPiasters = amountPiasters;
        this.currency = currency != null ? currency : "EGP";
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getAmountPiasters() {
        return amountPiasters;
    }

    public void setAmountPiasters(long amountPiasters) {
        this.amountPiasters = amountPiasters;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPaymobTransactionId() {
        return paymobTransactionId;
    }

    public void setPaymobTransactionId(String paymobTransactionId) {
        this.paymobTransactionId = paymobTransactionId;
    }

    public String getPaymobOrderId() {
        return paymobOrderId;
    }

    public void setPaymobOrderId(String paymobOrderId) {
        this.paymobOrderId = paymobOrderId;
    }

    public UUID getPlanId() {
        return planId;
    }

    public void setPlanId(UUID planId) {
        this.planId = planId;
    }

    public String getCardToken() {
        return cardToken;
    }

    public void setCardToken(String cardToken) {
        this.cardToken = cardToken;
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

    public String getTenantDisplayName() {
        return tenantDisplayName;
    }

    public void setTenantDisplayName(String tenantDisplayName) {
        this.tenantDisplayName = tenantDisplayName;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "id=" + id +
                ", tenantId=" + tenantId +
                ", tenantDisplayName='" + tenantDisplayName + '\'' +
                ", type='" + type + '\'' +
                ", amountPiasters=" + amountPiasters +
                ", currency='" + currency + '\'' +
                ", status='" + status + '\'' +
                ", paymobTransactionId='" + paymobTransactionId + '\'' +
                ", paymobOrderId='" + paymobOrderId + '\'' +
                ", planId=" + planId +
                ", cardToken='" + cardToken + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
