package com.nexusivr.ai.model.flow;

import java.util.Objects;

/**
 * Transfer to a live agent or external number.
 */
public class FlowTransfer {
    private String destination;
    private String callerId;
    private int timeoutSeconds = 30;

    public FlowTransfer() {
    }

    public FlowTransfer(String destination) {
        this.destination = destination;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getCallerId() {
        return callerId;
    }

    public void setCallerId(String callerId) {
        this.callerId = callerId;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String toString() {
        return "FlowTransfer{" +
                "destination='" + destination + '\'' +
                '}';
    }
}
