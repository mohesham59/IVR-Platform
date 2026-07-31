package com.nexusivr.ai.ai.agents;

import com.nexusivr.ai.ai.ProviderManager;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of specialized IVR agents.
 * <p>
 * Each agent is a text-only advisor. None of them produce FlowModel or VoiceXML.
 * The Java engine remains the sole owner of the flow graph.
 * </p>
 */
public class SpecializedAgentRegistry {

    private static final Map<String, SpecializedAgent> AGENTS = new LinkedHashMap<>();

    static {
        AGENTS.put("business_planner", null);
        AGENTS.put("conversation_designer", null);
        AGENTS.put("routing_expert", null);
        AGENTS.put("voice_prompt_writer", null);
        AGENTS.put("optimization_advisor", null);
        AGENTS.put("validator_assistant", null);
    }

    private SpecializedAgentRegistry() {
    }

    public static SpecializedAgent getAgent(String id) {
        if (id == null) return null;
        return AGENTS.get(id.toLowerCase().trim());
    }

    public static Map<String, SpecializedAgent> getAllAgents() {
        return Collections.unmodifiableMap(AGENTS);
    }

    /**
     * Initialize all agents with the given ProviderManager.
     * Call this once at startup (e.g., from ServiceRegistry).
     */
    public static synchronized void initialize(ProviderManager providerManager) {
        if (providerManager == null) return;
        AGENTS.replaceAll((id, agent) -> createAgent(id, providerManager));
    }

    private static SpecializedAgent createAgent(String id, ProviderManager pm) {
        return switch (id) {
            case "business_planner" -> new BusinessPlannerAgent(pm);
            case "conversation_designer" -> new ConversationDesignerAgent(pm);
            case "routing_expert" -> new RoutingExpertAgent(pm);
            case "voice_prompt_writer" -> new VoicePromptWriterAgent(pm);
            case "optimization_advisor" -> new OptimizationAdvisorAgent(pm);
            case "validator_assistant" -> new ValidatorAssistantAgent(pm);
            default -> throw new IllegalArgumentException("Unknown agent: " + id);
        };
    }
}
