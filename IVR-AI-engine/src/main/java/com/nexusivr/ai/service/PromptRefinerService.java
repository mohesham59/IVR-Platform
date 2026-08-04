package com.nexusivr.ai.service;

import com.nexusivr.ai.ai.AiResponse;
import com.nexusivr.ai.ai.ProviderManager;
import com.nexusivr.ai.config.GlobalAiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Pass 1 of the two-generation pipeline: takes a raw user prompt and refines it
 * into a structured, complete IVR specification using an LLM call.
 *
 * <p>This replaces the old rule-based {@link IvrPlanningEngine} for generation purposes.
 * The LLM infers the business domain, identifies missing elements, and produces a
 * clean specification that Pass 2 then turns into the actual flow.
 */
public class PromptRefinerService {

    private static final Logger logger = LoggerFactory.getLogger(PromptRefinerService.class);

    private static final String REFINER_SYSTEM_INSTRUCTION = """
        You are the NexusIVR Prompt Refiner. Your job is to analyze a raw user request for an IVR flow
        and produce a structured, complete specification ready for flow generation.

        CRITICAL RULES:
        - Infer the business domain from context (e.g., restaurant, banking, healthcare, telecom, insurance, retail, education, hospitality, government, airline, technical_support, legal, real_estate, logistics, gym, or generic).
        - Do NOT restrict yourself to a fixed list of domains — infer whatever descriptive domain fits the user's business description.
        - LANGUAGE PRESERVATION: If the user request is in Arabic (or any non-English language), ALL extracted text fields ("refined_prompt", "departments", "features", "menu_options", "greeting", "closing") MUST be in that SAME language (e.g., Arabic). Do not translate department names or prompts into English unless explicitly requested.
        - Identify any missing standard IVR elements (greeting, main menu, department routing, hours, closing, error handling, transfers) and fill them with sensible defaults.
        - Preserve ALL of the user's explicit requirements. EVERY SINGLE feature, department, capability, or option named or implied in the user prompt MUST be listed in the "departments" or "features" array. Never leave features ONLY in the free-text "refined_prompt" field!
        - The structured "departments" and "features" arrays are the SINGLE SOURCE OF TRUTH for Stage 2 flow generation.

        OUTPUT FORMAT (JSON):
        {
          "refined_prompt": "A complete, detailed description of the IVR flow requirements",
          "business_domain": "inferred domain",
          "departments": ["Dept A", "Dept B"],
          "features": ["Feature 1", "Feature 2"],
          "menu_options": ["Press 1 for ...", "Press 2 for ..."],
          "greeting": "Welcome message text",
          "closing": "Goodbye message text",
          "business_hours": "optional hours text or null",
          "transfers": ["Agent", "Department"],
          "error_handling": "brief description",
          "authentication": "brief description or null",
          "tone": "professional|casual|friendly|formal"
        }
        """;

    private final ProviderManager providerManager;

    public PromptRefinerService(ProviderManager providerManager) {
        this.providerManager = providerManager;
    }

    /**
     * Refines the user prompt into a structured specification.
     *
     * @param userPrompt     the raw user prompt
     * @param provider       primary provider name (may be null)
     * @param model          model name (may be null)
     * @param temp           temperature
     * @param timeout        timeout in seconds
     * @return the refined specification as a JSON string
     * @throws com.nexusivr.ai.service.exception.ProviderException if all providers fail
     */
    public String refine(String userPrompt, String provider, String model, double temp, int timeout) {
        return refine(userPrompt, provider, model, temp, timeout, List.of(), null);
    }

    public String refine(String userPrompt, String provider, String model, double temp, int timeout, com.nexusivr.ai.model.Message history) {
        return refine(userPrompt, provider, model, temp, timeout, history != null ? List.of(history) : List.of(), null);
    }

    public String refine(String userPrompt, String provider, String model, double temp, int timeout, List<com.nexusivr.ai.model.Message> history, String explicitDomain) {
        if (userPrompt == null || userPrompt.isBlank()) {
            return userPrompt;
        }

        logger.info("[PromptRefinerService] Starting Pass 1: Prompt Refinement. Prompt length={}, historySize={}, domain='{}'",
                userPrompt.length(), history != null ? history.size() : 0, explicitDomain);

        String systemInstruction = REFINER_SYSTEM_INSTRUCTION;
        if (GlobalAiConfig.getInstance().getSystemPrompt() != null
                && !GlobalAiConfig.getInstance().getSystemPrompt().equals(com.nexusivr.ai.ai.PromptBuilder.DEFAULT_SYSTEM_INSTRUCTION)) {
            systemInstruction = GlobalAiConfig.getInstance().getSystemPrompt() + "\n\n" + REFINER_SYSTEM_INSTRUCTION;
        }

        String domainHint = (explicitDomain != null && !explicitDomain.isBlank()) ? explicitDomain : DomainDetector.detect(userPrompt);

        String promptWithContext = userPrompt;
        if (history != null && !history.isEmpty()) {
            StringBuilder contextSb = new StringBuilder("Recent conversation context:\n");
            int count = 0;
            for (com.nexusivr.ai.model.Message msg : history) {
                if (msg.getContent() != null && !msg.getContent().isBlank()) {
                    contextSb.append(msg.getRole() != null ? msg.getRole().name() : "USER")
                             .append(": ")
                             .append(msg.getContent())
                             .append("\n");
                    count++;
                }
            }
            if (count > 0) {
                contextSb.append("\nCurrent user request:\n").append(userPrompt);
                promptWithContext = contextSb.toString();
            }
        }

        List<com.nexusivr.ai.dto.response.QuotaWarning> quotaWarnings = new java.util.ArrayList<>();
        AiResponse response = null;
        try {
            response = providerManager.executeWithRetryAndFallback(
                    provider, model, temp, timeout,
                    systemInstruction, promptWithContext, "PROMPT_REFINER", quotaWarnings, domainHint,
                    true
            );
        } catch (Exception e) {
            logger.warn("[PromptRefinerService] Pass 1 failed with exception: {}. Skipping refinement and passing raw prompt directly to Stage 2 (unrefined).", e.getMessage());
            return userPrompt;
        }

        if (response == null || response.isMock() || response.isTemplateFallback()) {
            logger.warn("[PromptRefinerService] Pass 1 failed (all LLM providers unavailable). Skipping refinement and passing raw prompt directly to Stage 2 (unrefined).");
            return userPrompt;
        }

        String refined = response.getContent();
        if (refined != null && isValidRefinedJsonSpec(refined)) {
            logger.info("[PromptRefinerService] Pass 1 complete. Refined spec length={} chars", refined.length());
            return refined;
        }

        // Business Logic Validation Failure: Retry Pass 1 ONCE
        logger.warn("[PromptRefinerService] Pass 1 output failed business-logic validation. Retrying Pass 1 attempt 2 of 2...");
        String retryPrompt = promptWithContext + "\n\nRETRY REMINDER: You MUST return a valid JSON object containing non-empty 'business_domain', 'departments' array, and matching 'menu_options' array.";

        try {
            response = providerManager.executeWithRetryAndFallback(
                    provider, model, temp, timeout,
                    systemInstruction, retryPrompt, "PROMPT_REFINER", quotaWarnings, domainHint,
                    true
            );
            if (response != null && !response.isMock() && !response.isTemplateFallback() && response.getContent() != null) {
                refined = response.getContent();
                if (isValidRefinedJsonSpec(refined)) {
                    logger.info("[PromptRefinerService] Pass 1 retry succeeded. Refined spec length={} chars", refined.length());
                    return refined;
                }
            }
        } catch (Exception ignored) {}

        logger.warn("[PromptRefinerService] Pass 1 retry also failed validation. Discarding invalid spec and forwarding ORIGINAL raw prompt directly to Stage 2 (unrefined).");
        return userPrompt;
    }

    public static boolean isValidRefinedJsonSpec(String text) {
        if (text == null || text.isBlank()) return false;
        String trimmed = text.trim();

        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            trimmed = trimmed.trim();
        }

        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return false;
        }

        try {
            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(trimmed).getAsJsonObject();
            
            // Rule 1: Must contain non-empty business_domain
            if (!obj.has("business_domain") || obj.get("business_domain").isJsonNull() || obj.get("business_domain").getAsString().isBlank()) {
                return false;
            }

            // Rule 2: Must contain non-empty departments array
            if (!obj.has("departments") || !obj.get("departments").isJsonArray() || obj.getAsJsonArray("departments").size() == 0) {
                return false;
            }

            // Rule 3: Must contain non-empty menu_options array
            if (!obj.has("menu_options") || !obj.get("menu_options").isJsonArray() || obj.getAsJsonArray("menu_options").size() == 0) {
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
