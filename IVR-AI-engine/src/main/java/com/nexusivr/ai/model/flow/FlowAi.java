package com.nexusivr.ai.model.flow;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI bot agent node.
 *
 * <p>Supports two rendering modes:
 * <ol>
 *   <li><b>Subdialog mode</b> (legacy): {@code agentId} is set → emits
 *       {@code <subdialog src="ai://agentId"/>}.</li>
 *   <li><b>AI-routing mode</b>: {@code role} is set → emits the custom engine tag
 *       {@code <ai role="..." options="label:targetId,...">} with the routing
 *       options derived from {@code routingOptions} or from the node's outgoing
 *       connections.</li>
 * </ol>
 */
public class FlowAi {

    /** Legacy agent identifier for subdialog mode. */
    private String agentId;

    /** Human-readable prompt text displayed/spoken to the caller. */
    private String prompt;

    /** Maximum conversation turns (subdialog mode). */
    private int maxTurns = 5;

    /**
     * AI persona / system-prompt string for routing mode.
     * When non-null/non-blank, the exporter emits {@code <ai role="...">}.
     */
    private String role;

    /**
     * Ordered map of routing options for AI-routing mode: label → targetNodeId.
     * Serialised as the {@code options} attribute: {@code "label1:target1, label2:target2"}.
     * If null or empty, the exporter falls back to building options from the
     * node's outgoing connections.
     */
    private LinkedHashMap<String, String> routingOptions;

    public FlowAi() {
    }

    public FlowAi(String agentId, String prompt) {
        this.agentId = agentId;
        this.prompt = prompt;
    }

    // ---- agentId ----

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    // ---- prompt ----

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    // ---- maxTurns ----

    public int getMaxTurns() {
        return maxTurns;
    }

    public void setMaxTurns(int maxTurns) {
        this.maxTurns = maxTurns;
    }

    // ---- role ----

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    // ---- routingOptions ----

    public LinkedHashMap<String, String> getRoutingOptions() {
        return routingOptions;
    }

    public void setRoutingOptions(LinkedHashMap<String, String> routingOptions) {
        this.routingOptions = routingOptions;
    }

    /**
     * Returns true if this node should be rendered in AI-routing mode
     * (i.e. {@code role} is set), false for legacy subdialog mode.
     */
    public boolean isRoutingMode() {
        return role != null && !role.isBlank();
    }

    @Override
    public String toString() {
        return "FlowAi{" +
                "agentId='" + agentId + '\'' +
                ", role='" + role + '\'' +
                '}';
    }
}
