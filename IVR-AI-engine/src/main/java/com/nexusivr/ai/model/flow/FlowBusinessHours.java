package com.nexusivr.ai.model.flow;

import java.util.Objects;

/**
 * Business hours check.
 */
public class FlowBusinessHours {
    private String openTime; // HH:MM format
    private String closeTime; // HH:MM format
    private String timezone = "UTC";
    private String openTargetNodeId;
    private String closedTargetNodeId;

    public FlowBusinessHours() {
    }

    public FlowBusinessHours(String openTime, String closeTime) {
        this.openTime = openTime;
        this.closeTime = closeTime;
    }

    public String getOpenTime() {
        return openTime;
    }

    public void setOpenTime(String openTime) {
        this.openTime = openTime;
    }

    public String getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(String closeTime) {
        this.closeTime = closeTime;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getOpenTargetNodeId() {
        return openTargetNodeId;
    }

    public void setOpenTargetNodeId(String openTargetNodeId) {
        this.openTargetNodeId = openTargetNodeId;
    }

    public String getClosedTargetNodeId() {
        return closedTargetNodeId;
    }

    public void setClosedTargetNodeId(String closedTargetNodeId) {
        this.closedTargetNodeId = closedTargetNodeId;
    }

    @Override
    public String toString() {
        return "FlowBusinessHours{" +
                "openTime='" + openTime + '\'' +
                ", closeTime='" + closeTime + '\'' +
                '}';
    }
}
