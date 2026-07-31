package com.nexusivr.ai.dto.response;

import com.nexusivr.ai.dto.common.AnalyticsMetricDto;
import com.nexusivr.ai.dto.common.PageResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Output of the Analytics module. `metrics` carries the top-line, always-
 * present numbers (total sessions, avg duration, etc.); `breakdown` is
 * the optional paginated per-group table that only exists when the
 * request specified groupBy — each row is a Map keyed by the requested
 * dimensions plus a "value" entry, kept generic for the same reason
 * AnalyticsMetricDto is generic (no analytics table yet to fix a shape).
 */
public class AnalyticsResponse {

    private List<AnalyticsMetricDto> metrics;
    private PageResponse<Map<String, Object>> breakdown;
    private Instant generatedAt;

    public AnalyticsResponse() {
        this.metrics = new ArrayList<>();
    }

    public AnalyticsResponse(List<AnalyticsMetricDto> metrics, PageResponse<Map<String, Object>> breakdown,
                              Instant generatedAt) {
        this.metrics = metrics != null ? metrics : new ArrayList<>();
        this.breakdown = breakdown;
        this.generatedAt = generatedAt;
    }

    public List<AnalyticsMetricDto> getMetrics() { return metrics; }
    public void setMetrics(List<AnalyticsMetricDto> metrics) { this.metrics = metrics; }

    public PageResponse<Map<String, Object>> getBreakdown() { return breakdown; }
    public void setBreakdown(PageResponse<Map<String, Object>> breakdown) { this.breakdown = breakdown; }

    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }

    @Override
    public String toString() {
        return "AnalyticsResponse{" +
                "metrics=" + metrics +
                ", breakdown=" + breakdown +
                ", generatedAt=" + generatedAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnalyticsResponse)) return false;
        AnalyticsResponse that = (AnalyticsResponse) o;
        return Objects.equals(metrics, that.metrics) && Objects.equals(breakdown, that.breakdown) &&
                Objects.equals(generatedAt, that.generatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metrics, breakdown, generatedAt);
    }
}
