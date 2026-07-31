package com.nexusivr.ai.ai;

import com.nexusivr.ai.model.Message;

import java.util.List;

/**
 * Strategy interface for AI provider integrations (Gemini, Groq, Ollama).
 * Decouples business logic from specific LLM providers.
 */
public interface AiProvider {

    /**
     * Generates a free-form conversational AI response given a prompt and conversation history.
     *
     * @param prompt  the input user prompt or instruction
     * @param history recent message turns in the conversation session
     * @return standardized {@link AiResponse} containing output content and metadata
     */
    AiResponse generateResponse(String prompt, List<Message> history);

    /**
     * Generates a free-form conversational AI response with explicit system context.
     *
     * @param systemPrompt global role or domain instruction
     * @param userPrompt   the specific user prompt
     * @param history      recent message turns in the conversation session
     * @return standardized {@link AiResponse} containing output content and metadata
     */
    AiResponse generateResponse(String systemPrompt, String userPrompt, List<Message> history);

    /**
     * Generates a structured (e.g. JSON) AI response.
     *
     * @param prompt  the input prompt requesting structured output
     * @param history recent message turns in the conversation session
     * @return standardized {@link AiResponse} containing output JSON and metadata
     */
    AiResponse generateStructuredResponse(String prompt, List<Message> history);

    /**
     * Generates a structured (e.g. JSON) AI response with explicit system context.
     *
     * @param systemPrompt global role or domain instruction
     * @param userPrompt   the specific user prompt
     * @param history      recent message turns in the conversation session
     * @return standardized {@link AiResponse} containing output JSON and metadata
     */
    AiResponse generateStructuredResponse(String systemPrompt, String userPrompt, List<Message> history);
}
