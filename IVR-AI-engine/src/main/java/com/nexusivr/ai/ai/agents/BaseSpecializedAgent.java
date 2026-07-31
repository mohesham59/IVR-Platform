package com.nexusivr.ai.ai.agents;

import com.nexusivr.ai.ai.AiResponse;
import com.nexusivr.ai.ai.LlmClient;
import com.nexusivr.ai.ai.ProviderManager;
import com.nexusivr.ai.config.GlobalAiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * Base class for all specialized agents.
 * <p>
 * Handles LLM resolution, prompt construction, and response extraction.
 * Subclasses only need to provide:
 * <ul>
 *   <li>Agent identity (id, name, description)</li>
 *   <li>A system instruction defining the agent's persona and constraints</li>
 *   <li>A user-prompt formatter</li>
 * </ul>
 */
public abstract class BaseSpecializedAgent implements SpecializedAgent {

    private static final Logger logger = LoggerFactory.getLogger(BaseSpecializedAgent.class);

    private final String id;
    private final String name;
    private final String description;
    private final String systemInstruction;
    private final ProviderManager providerManager;

    protected BaseSpecializedAgent(String id, String name, String description, String systemInstruction, ProviderManager providerManager) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.systemInstruction = systemInstruction;
        this.providerManager = providerManager;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    /**
     * Build the user-facing prompt from the raw context.
     */
    protected abstract String buildUserPrompt(String context);

    @Override
    public String advise(String context) {
        double temp = GlobalAiConfig.getInstance().getTemperature();
        int timeout = GlobalAiConfig.getInstance().getTimeout();
        String provider = GlobalAiConfig.getInstance().getProvider();
        String model = GlobalAiConfig.getInstance().getModel();
        return advise(context, provider, model, temp, timeout);
    }

    @Override
    public String advise(String context, String provider, String model, double temperature, int timeoutSeconds) {
        if (context == null || context.isBlank()) {
            return "";
        }

        String userPrompt = buildUserPrompt(context);
        String effectiveProvider = (provider != null) ? provider : GlobalAiConfig.getInstance().getProvider();
        String effectiveModel = (model != null) ? model : GlobalAiConfig.getInstance().getModel();
        double effectiveTemp = temperature >= 0 ? temperature : GlobalAiConfig.getInstance().getTemperature();
        int effectiveTimeout = timeoutSeconds > 0 ? timeoutSeconds : GlobalAiConfig.getInstance().getTimeout();

        try {
            AiResponse response = providerManager.executeWithRetryAndFallback(
                    effectiveProvider,
                    effectiveModel,
                    effectiveTemp,
                    effectiveTimeout,
                    systemInstruction,
                    userPrompt,
                    "AGENT_" + id.toUpperCase(),
                    Collections.emptyList(),
                    "generic",
                    false
            );

            if (response != null && response.getContent() != null && !response.getContent().isBlank()) {
                return response.getContent().trim();
            }

            logger.warn("[{}] Agent received empty response from LLM", id);
            return fallbackAdvice(context);
        } catch (Exception e) {
            logger.error("[{}] Agent LLM call failed: {}", id, e.getMessage(), e);
            return fallbackAdvice(context);
        }
    }

    /**
     * Fallback advice when the LLM is unavailable.
     */
    protected String fallbackAdvice(String context) {
        return "[" + name + "] Unable to generate advice at this time. Please try again later.";
    }
}
