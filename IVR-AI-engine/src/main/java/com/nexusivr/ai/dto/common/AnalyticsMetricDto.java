package com.nexusivr.ai.dto.common;

import java.util.Objects;

/**
 * A single named metric value in an analytics result (e.g. "avg_session_
 * duration_seconds" -> 184.2, "total_sessions" -> 5230). Deliberately
 * generic/name-value rather than a fixed set of typed fields, because
 * there is no analytics table in the MVP schema — the DB design doc
 * defers `analytics` to v2 as an event-stream-backed fact table — so
 * today's metrics are computed ad hoc over ai_sessions/ai_messages and
 * the set of available metrics is expected to grow before any schema
 * exists to constrain it.
 */
public class AnalyticsMetricDto {

    private String name;
    private double value;
    private String unit;

    public AnalyticsMetricDto() {
    }

    public AnalyticsMetricDto(String name, double value, String unit) {
        this.name = name;
        this.value = value;
        this.unit = unit;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    @Override
    public String toString() {
        return "AnalyticsMetricDto{" +
                "name='" + name + '\'' +
                ", value=" + value +
                ", unit='" + unit + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnalyticsMetricDto)) return false;
        AnalyticsMetricDto that = (AnalyticsMetricDto) o;
        return Double.compare(that.value, value) == 0 && Objects.equals(name, that.name) &&
                Objects.equals(unit, that.unit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value, unit);
    }
}
