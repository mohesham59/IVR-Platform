package com.nexusivr.ai.model.flow;

import java.util.Objects;

/**
 * Database lookup node.
 */
public class FlowDatabase {
    private String query;
    private String connection;
    private int timeoutSeconds = 5;

    public FlowDatabase() {
    }

    public FlowDatabase(String query, String connection) {
        this.query = query;
        this.connection = connection;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getConnection() {
        return connection;
    }

    public void setConnection(String connection) {
        this.connection = connection;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String toString() {
        return "FlowDatabase{" +
                "query='" + query + '\'' +
                '}';
    }
}
