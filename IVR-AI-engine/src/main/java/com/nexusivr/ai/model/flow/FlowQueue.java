package com.nexusivr.ai.model.flow;

import java.util.Objects;

/**
 * Call queue configuration.
 */
public class FlowQueue {
    private String queueName;
    private String musicOnHold;
    private int maxWaitSeconds = 300;
    private int maxCallers = 10;

    public FlowQueue() {
    }

    public FlowQueue(String queueName) {
        this.queueName = queueName;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public String getMusicOnHold() {
        return musicOnHold;
    }

    public void setMusicOnHold(String musicOnHold) {
        this.musicOnHold = musicOnHold;
    }

    public int getMaxWaitSeconds() {
        return maxWaitSeconds;
    }

    public void setMaxWaitSeconds(int maxWaitSeconds) {
        this.maxWaitSeconds = maxWaitSeconds;
    }

    public int getMaxCallers() {
        return maxCallers;
    }

    public void setMaxCallers(int maxCallers) {
        this.maxCallers = maxCallers;
    }

    @Override
    public String toString() {
        return "FlowQueue{" +
                "queueName='" + queueName + '\'' +
                '}';
    }
}
