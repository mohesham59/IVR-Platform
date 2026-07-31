package com.nexusivr.ai.dto.request;

import com.nexusivr.ai.dto.common.PageRequest;

import java.util.Objects;
import java.util.UUID;

/**
 * Input for the Conversation module's lookup of a customer's
 * cross-session memory: "give me everything conversation_history has
 * for this customer, paginated." tenantId + customerIdentifier together
 * mirror the composite index the DB design doc defines on
 * conversation_history (tenant_id, customer_identifier, created_at DESC),
 * so this query maps directly onto that index rather than requiring a
 * broader scan.
 */
public class ConversationHistoryRequest {

    private UUID tenantId;
    private String customerIdentifier;
    private PageRequest pagination;

    public ConversationHistoryRequest() {
        this.pagination = new PageRequest();
    }

    public ConversationHistoryRequest(UUID tenantId, String customerIdentifier, PageRequest pagination) {
        this.tenantId = tenantId;
        this.customerIdentifier = customerIdentifier;
        this.pagination = pagination != null ? pagination : new PageRequest();
    }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getCustomerIdentifier() { return customerIdentifier; }
    public void setCustomerIdentifier(String customerIdentifier) { this.customerIdentifier = customerIdentifier; }

    public PageRequest getPagination() { return pagination; }
    public void setPagination(PageRequest pagination) { this.pagination = pagination; }

    @Override
    public String toString() {
        return "ConversationHistoryRequest{" +
                "tenantId=" + tenantId +
                ", customerIdentifier='" + customerIdentifier + '\'' +
                ", pagination=" + pagination +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConversationHistoryRequest)) return false;
        ConversationHistoryRequest that = (ConversationHistoryRequest) o;
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(customerIdentifier, that.customerIdentifier) &&
                Objects.equals(pagination, that.pagination);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, customerIdentifier, pagination);
    }
}
