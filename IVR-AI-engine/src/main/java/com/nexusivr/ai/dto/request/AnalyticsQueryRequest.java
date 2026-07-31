package com.nexusivr.ai.dto.request;

import com.nexusivr.ai.dto.common.PageRequest;
import com.nexusivr.ai.model.Channel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Input for the Analytics module. Modeled as an ad hoc query (date range
 * + optional channel filter + groupBy dimensions) rather than a fixed
 * set of report types, since there is no `analytics` table in the MVP
 * schema to pin the shape of a "report" to — everything here is computed
 * on demand over ai_sessions/ai_messages. pagination is optional and
 * only used when the query's groupBy produces a row-per-group breakdown
 * large enough to need paging (e.g. grouping by day over a year).
 */
public class AnalyticsQueryRequest {

    private UUID tenantId;
    private Instant startDate;
    private Instant endDate;
    private Channel channelFilter;
    private List<String> groupBy;
    private PageRequest pagination;

    public AnalyticsQueryRequest() {
        this.groupBy = new ArrayList<>();
    }

    public AnalyticsQueryRequest(UUID tenantId, Instant startDate, Instant endDate, Channel channelFilter,
                                  List<String> groupBy, PageRequest pagination) {
        this.tenantId = tenantId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.channelFilter = channelFilter;
        this.groupBy = groupBy != null ? groupBy : new ArrayList<>();
        this.pagination = pagination;
    }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public Instant getStartDate() { return startDate; }
    public void setStartDate(Instant startDate) { this.startDate = startDate; }

    public Instant getEndDate() { return endDate; }
    public void setEndDate(Instant endDate) { this.endDate = endDate; }

    public Channel getChannelFilter() { return channelFilter; }
    public void setChannelFilter(Channel channelFilter) { this.channelFilter = channelFilter; }

    public List<String> getGroupBy() { return groupBy; }
    public void setGroupBy(List<String> groupBy) { this.groupBy = groupBy; }

    public PageRequest getPagination() { return pagination; }
    public void setPagination(PageRequest pagination) { this.pagination = pagination; }

    @Override
    public String toString() {
        return "AnalyticsQueryRequest{" +
                "tenantId=" + tenantId +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", channelFilter=" + channelFilter +
                ", groupBy=" + groupBy +
                ", pagination=" + pagination +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnalyticsQueryRequest)) return false;
        AnalyticsQueryRequest that = (AnalyticsQueryRequest) o;
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(startDate, that.startDate) &&
                Objects.equals(endDate, that.endDate) && channelFilter == that.channelFilter &&
                Objects.equals(groupBy, that.groupBy) && Objects.equals(pagination, that.pagination);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, startDate, endDate, channelFilter, groupBy, pagination);
    }
}
