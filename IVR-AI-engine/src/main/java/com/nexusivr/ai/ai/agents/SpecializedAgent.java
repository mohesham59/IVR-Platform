package com.nexusivr.ai.ai.agents;

import com.nexusivr.ai.ai.AiResponse;
import com.nexusivr.ai.ai.LlmClient;
import com.nexusivr.ai.ai.ProviderManager;

import java.util.List;

/**
 * Contract for all specialized IVR agents.
 * <p>
 * Every specialized agent is a <b>text-only advisor</b>. It must never
 * produce a {@code FlowModel}, VoiceXML, or any other graph artifact.
 * The Java engine remains the sole owner of the flow graph.
 * </p>
 */
public interface SpecializedAgent {

    /**
     * @return unique agent identifier
     */
    String getId();

    /**
     * @return human-readable agent name
     */
    String getName();

    /**
     * @return short description of what this agent advises on
     */
    String getDescription();

    /**
     * Produce advice for the given context.
     *
     * @param context free-form text describing the situation; may include
     *                business description, validation issues, node metadata, etc.
     * @return plain-text advice; never VoiceXML, JSON flow, or graph objects
     */
    String advise(String context);

    /**
     * Produce advice using a specific LLM provider/model override.
     */
    String advise(String context, String provider, String model, double temperature, int timeoutSeconds);
}
