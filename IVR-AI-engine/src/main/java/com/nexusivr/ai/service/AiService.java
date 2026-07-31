package com.nexusivr.ai.service;

import com.nexusivr.ai.ai.AiProvider;
import com.nexusivr.ai.ai.AiResponse;
import com.nexusivr.ai.ai.FunctionExecutor;
import com.nexusivr.ai.ai.PromptBuilder;
import com.nexusivr.ai.ai.ProviderManager;
import com.nexusivr.ai.dto.common.FlowDto;
import com.nexusivr.ai.dto.common.SentimentLabel;
import com.nexusivr.ai.dto.common.SentimentScoreDto;
import com.nexusivr.ai.dto.common.ValidationIssueDto;
import com.nexusivr.ai.dto.common.ValidationSeverity;
import com.nexusivr.ai.dto.response.FlowImprovementResponse;
import com.nexusivr.ai.dto.response.FlowValidationResponse;
import com.nexusivr.ai.dto.response.QuotaWarning;
import com.nexusivr.ai.dto.response.SentimentAnalysisResponse;
import com.nexusivr.ai.model.Flow;
import com.nexusivr.ai.model.Message;
import com.nexusivr.ai.service.exception.ProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * High-level AI capability orchestrator service.
 * Coordinates {@link PromptBuilder}, {@link AiProvider}, and {@link FunctionExecutor}.
 * Serves as the primary entry point for higher-level domain services like {@link ChatService} and {@link FlowService}.
 */
public class AiService {

    private static final Logger logger = LoggerFactory.getLogger(AiService.class);

    private final PromptBuilder promptBuilder;
    private final AiProvider aiProvider;
    private final FunctionExecutor functionExecutor;
    private final ProviderManager providerManager;
    private static final ThreadLocal<AiResponse> lastProviderResponse = new ThreadLocal<>();

    public AiService() {
        this(new PromptBuilder(), com.nexusivr.ai.ai.LlmProviderFactory.getLlmClient(), new FunctionExecutor(), null);
    }

    public AiService(String providerName) {
        this(new PromptBuilder(), com.nexusivr.ai.ai.LlmProviderFactory.getLlmClient(providerName), new FunctionExecutor(), null);
    }

    public AiService(PromptBuilder promptBuilder, AiProvider aiProvider, FunctionExecutor functionExecutor) {
        this(promptBuilder, aiProvider, functionExecutor, null);
    }

    public AiService(PromptBuilder promptBuilder, AiProvider aiProvider, FunctionExecutor functionExecutor, ProviderManager providerManager) {
        this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder must not be null");
        this.aiProvider = Objects.requireNonNull(aiProvider, "aiProvider must not be null");
        this.functionExecutor = Objects.requireNonNull(functionExecutor, "functionExecutor must not be null");
        this.providerManager = providerManager;
    }

    public static AiResponse getLastProviderResponse() {
        return lastProviderResponse.get();
    }

    public static void clearLastProviderResponse() {
        lastProviderResponse.remove();
    }

    /**
     * Generates a conversational AI assistant response for a prompt and conversation history.
     */
    public String generateResponse(String prompt, List<Message> history) {
        return generateResponse(prompt, history, null);
    }

    /**
     * Generates a conversational AI assistant response with active IVR flow context.
     */
    public String generateResponse(String prompt, List<Message> history, String flowContextJson) {
        logger.debug("Generating AI response with PromptBuilder and AiProvider for prompt: {}", prompt);
        String userPrompt = (flowContextJson != null && !flowContextJson.isBlank())
                ? promptBuilder.buildContextualPrompt(prompt, flowContextJson)
                : prompt;

        String systemInstruction = com.nexusivr.ai.config.GlobalAiConfig.getInstance().getSystemPrompt();
        if (systemInstruction == null || systemInstruction.equals(com.nexusivr.ai.ai.PromptBuilder.DEFAULT_SYSTEM_INSTRUCTION)) {
            systemInstruction = promptBuilder.getSystemInstruction();
        }

        if (providerManager != null) {
            try {
                String provider = com.nexusivr.ai.config.GlobalAiConfig.getInstance().getProvider();
                String model = com.nexusivr.ai.config.LlmConfig.getGroqGenerationModel();
                double temp = com.nexusivr.ai.config.GlobalAiConfig.getInstance().getTemperature();
                int timeout = com.nexusivr.ai.config.GlobalAiConfig.getInstance().getTimeout();
                AiResponse response = providerManager.executeWithRetryAndFallback(
                        provider, model, temp, timeout,
                        systemInstruction, userPrompt, "CHAT", List.of(), "generic", false
                );
                lastProviderResponse.set(response);
                return response.getContent();
            } catch (ProviderException e) {
                logger.warn("[AiService] ProviderManager failed for chat response: {}. Falling back to direct provider.", e.getMessage());
            }
        }

        String selectedProvider = com.nexusivr.ai.config.GlobalAiConfig.getInstance().getProvider();
        AiProvider effectiveProvider = aiProvider;
        if (providerManager != null && !selectedProvider.equalsIgnoreCase("mock")) {
            try {
                AiProvider selectedAiProvider = com.nexusivr.ai.ai.LlmProviderFactory.getLlmClient(selectedProvider);
                if (selectedAiProvider != null) {
                    effectiveProvider = selectedAiProvider;
                }
            } catch (Exception ex) {
                logger.warn("[AiService] Could not get provider '{}' for fallback: {}", selectedProvider, ex.getMessage());
            }
        }

        AiResponse response = effectiveProvider.generateResponse(
                systemInstruction,
                userPrompt,
                history != null ? history : List.of()
        );
        lastProviderResponse.set(response);
        return response.getContent();
    }

    /**
     * Generates a structured JSON IVR flow definition based on business requirements.
     */
    public Flow generateFlow(String businessDescription) {
        logger.debug("Generating structured IVR flow via UnifiedAiEngine for business description: {}", businessDescription);
        Flow flow = com.nexusivr.ai.controller.ServiceRegistry.getUnifiedAiEngine().generateFlow(
                null, null, businessDescription, null, null, -1.0, -1
        );
        return flow;
    }

    /**
     * Analyzes and suggests improvements for an existing IVR flow JSON.
     */
    public FlowImprovementResponse improveFlow(String flowJson, String instructions) {
        logger.debug("Improving flow JSON via UnifiedAiEngine with instructions: {}", instructions);
        return com.nexusivr.ai.controller.ServiceRegistry.getUnifiedAiEngine().improveFlow(
                null, flowJson, instructions, null, null, -1.0, -1
        );
    }

    public FlowImprovementResponse improveFlow(String flowJson, String instructions,
                                               String provider, String model, double temp, int timeout) {
        logger.debug("Improving flow JSON via UnifiedAiEngine with provider={}, model={}", provider, model);
        return com.nexusivr.ai.controller.ServiceRegistry.getUnifiedAiEngine().improveFlow(
                null, flowJson, instructions, provider, model, temp, timeout
        );
    }

    /**
     * Validates an IVR flow (VoiceXML or Builder JSON) and identifies structural issues.
     */
    public FlowValidationResponse validateFlow(String flow) {
        logger.debug("Validating IVR flow via UnifiedAiEngine");
        return com.nexusivr.ai.controller.ServiceRegistry.getUnifiedAiEngine().validateFlow(flow);
    }

    /**
     * Performs sentiment analysis on transcript text or turns.
     */
    public SentimentAnalysisResponse analyzeSentiment(String text) {
        logger.debug("Analyzing sentiment for text: {}", text);

        try {
            return analyzeSentimentWithAi(text);
        } catch (Exception e) {
            logger.warn("AI sentiment analysis failed, falling back to local analysis.", e);
            return analyzeSentimentLocal(text);
        }
    }

    private SentimentAnalysisResponse analyzeSentimentWithAi(String text) {
        String prompt = "Analyze the sentiment of the following text and respond in JSON format with fields: " +
                "\"label\" (one of POSITIVE, NEGATIVE, NEUTRAL), \"score\" (float from -1.0 to 1.0), \"confidence\" (float from 0.0 to 1.0).\n\nText: " + text;
        try {
            if (providerManager != null) {
                String provider = com.nexusivr.ai.config.GlobalAiConfig.getInstance().getProvider();
                String model = com.nexusivr.ai.config.LlmConfig.getGroqGenerationModel();
                double temp = com.nexusivr.ai.config.GlobalAiConfig.getInstance().getTemperature();
                int timeout = com.nexusivr.ai.config.GlobalAiConfig.getInstance().getTimeout();
                AiResponse response = providerManager.executeWithRetryAndFallback(
                        provider, model, temp, timeout,
                        "You are a sentiment analysis engine. Respond only with valid JSON.", prompt,
                        "SENTIMENT", List.of(), "generic", true);
                return parseSentimentResponse(response.getContent());
            }
            AiResponse response = aiProvider.generateStructuredResponse(
                    "You are a sentiment analysis engine. Respond only with valid JSON.", prompt, List.of());
            return parseSentimentResponse(response.getContent());
        } catch (Exception e) {
            logger.warn("AI sentiment analysis failed, falling back to local analysis: {}", e.getMessage());
            return analyzeSentimentLocal(text);
        }
    }

    private SentimentAnalysisResponse parseSentimentResponse(String content) {
        com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
        String labelStr = json.has("label") ? json.get("label").getAsString().toUpperCase() : "NEUTRAL";
        SentimentLabel label = SentimentLabel.valueOf(labelStr);
        double score = json.has("score") ? json.get("score").getAsDouble() : 0.0;
        double confidence = json.has("confidence") ? json.get("confidence").getAsDouble() : 0.8;
        SentimentScoreDto overall = new SentimentScoreDto(label, score, confidence);
        return new SentimentAnalysisResponse(overall, List.of(overall));
    }

    private SentimentAnalysisResponse analyzeSentimentLocal(String text) {
        String lower = text != null ? text.toLowerCase() : "";

        SentimentLabel label = SentimentLabel.POSITIVE;
        double score = 0.85;
        double confidence = 0.70;

        if (lower.contains("frustrated") || lower.contains("bad") || lower.contains("terrible") || lower.contains("error") || lower.contains("issue") || lower.contains("angry") || lower.contains("hate")) {
            label = SentimentLabel.NEGATIVE;
            score = -0.72;
            confidence = 0.65;
        } else if (lower.contains("okay") || lower.contains("normal") || lower.contains("fine") || lower.contains("alright")) {
            label = SentimentLabel.NEUTRAL;
            score = 0.05;
            confidence = 0.60;
        }

        SentimentScoreDto overall = new SentimentScoreDto(label, score, confidence);
        return new SentimentAnalysisResponse(overall, List.of(overall));
    }

    /**
     * Summarizes a list of transcript messages into a concise summary.
     */
    public String summarizeConversation(List<Message> messages) {
        logger.debug("Summarizing conversation transcript with {} turns", messages != null ? messages.size() : 0);
        String summaryPrompt = promptBuilder.buildSummarizationPrompt(messages);
        try {
            if (providerManager != null) {
                String provider = com.nexusivr.ai.config.GlobalAiConfig.getInstance().getProvider();
                String model = com.nexusivr.ai.config.LlmConfig.getGroqGenerationModel();
                double temp = com.nexusivr.ai.config.GlobalAiConfig.getInstance().getTemperature();
                int timeout = com.nexusivr.ai.config.GlobalAiConfig.getInstance().getTimeout();
                AiResponse response = providerManager.executeWithRetryAndFallback(
                        provider, model, temp, timeout,
                        null, summaryPrompt, "SUMMARIZE", List.of(), "generic", false);
                return response.getContent();
            }
            AiResponse response = aiProvider.generateResponse(summaryPrompt, messages != null ? messages : List.of());
            return response.getContent();
        } catch (Exception e) {
            logger.warn("AI summarization failed: {}", e.getMessage());
            return "Conversation summary unavailable.";
        }
    }

    /**
     * Executes a registered AI function call (e.g. transfer call, check balance, create ticket).
     */
    public String executeFunction(String functionName, Map<String, Object> parameters) {
        logger.debug("Delegating function call execution for function: {}", functionName);
        return functionExecutor.executeFunction(functionName, parameters);
    }

    public PromptBuilder getPromptBuilder() {
        return promptBuilder;
    }

    public AiProvider getAiProvider() {
        return aiProvider;
    }

    public ProviderManager getProviderManager() {
        return providerManager;
    }

    public FunctionExecutor getFunctionExecutor() {
        return functionExecutor;
    }
}
