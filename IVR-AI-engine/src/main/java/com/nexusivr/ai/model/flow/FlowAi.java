package com.nexusivr.ai.model.flow;

import java.util.Objects;

/**
 * AI bot agent node.
 */
public class FlowAi {
    private String agentId;
    private String prompt;
    private int maxTurns = 5;

    public FlowAi() {
    }

    public FlowAi(String agentId, String prompt) {
        this.agentId = agentId;
        this.prompt = prompt;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public int getMaxTurns() {
        return maxTurns;
    }

    public void setMaxTurns(int maxTurns) {
        this.maxTurns = maxTurns;
    }

    @Override
    public String toString() {
        return "FlowAi{" +
                "agentId='" + agentId + '\'' +
                '}';
    }
}
