package com.nexusivr.ai.ai.agents;

import com.nexusivr.ai.ai.ProviderManager;

import java.util.Collections;
import java.util.Map;

/**
 * Service that routes requests to the appropriate specialized agent.
 * <p>
 * This is the only entry point for agent invocations.
 * Agents are text-only advisors; they never produce FlowModel or VoiceXML.
 * </p>
 */
public class SpecializedAgentService {

    private final Map<String, SpecializedAgent> agents;

    public SpecializedAgentService(ProviderManager providerManager) {
        this.agents = SpecializedAgentRegistry.getAllAgents();
        SpecializedAgentRegistry.initialize(providerManager);
    }

    /**
     * Invoke a specific agent by ID.
     *
     * @param agentId  one of: business_planner, conversation_designer, routing_expert,
     *                 voice_prompt_writer, optimization_advisor, validator_assistant
     * @param context  free-form text context for the agent
     * @return plain-text advice from the agent
     */
    public String invoke(String agentId, String context) {
        SpecializedAgent agent = agents.get(agentId.toLowerCase().trim());
        if (agent == null) {
            return "[SpecializedAgentService] Unknown agent: " + agentId + ". Available agents: " + String.join(", ", agents.keySet());
        }
        return agent.advise(context);
    }

    /**
     * Invoke a specific agent with provider/model overrides.
     */
    public String invoke(String agentId, String context, String provider, String model, double temperature, int timeoutSeconds) {
        SpecializedAgent agent = agents.get(agentId.toLowerCase().trim());
        if (agent == null) {
            return "[SpecializedAgentService] Unknown agent: " + agentId + ". Available agents: " + String.join(", ", agents.keySet());
        }
        return agent.advise(context, provider, model, temperature, timeoutSeconds);
    }

    /**
     * List all available specialized agents.
     */
    public Map<String, SpecializedAgent> listAgents() {
        return Collections.unmodifiableMap(agents);
    }
}
