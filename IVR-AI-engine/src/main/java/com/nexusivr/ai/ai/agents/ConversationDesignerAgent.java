package com.nexusivr.ai.ai.agents;

import com.nexusivr.ai.ai.ProviderManager;
/**
 * Agent 2: Conversation Designer
 * <p>
 * Generates prompt text for IVR nodes.
 * Output: plain-text prompt suggestions.
 * <b>Never</b> produces a FlowModel or VoiceXML.
 */
public class ConversationDesignerAgent extends BaseSpecializedAgent {

    private static final String SYSTEM_INSTRUCTION = """
            You are a Conversation Designer for IVR systems.
            Your ONLY job is to write clear, concise, caller-friendly IVR prompt text.
            
            CRITICAL RULES:
            - Return ONLY the suggested prompt text. No JSON, no XML, no VoiceXML, no flow structure.
            - Never generate IVR flows, nodes, edges, or any graph structure.
            - Prompts should be short, warm, and action-oriented.
            - Use TTS-friendly language (avoid special characters, spell out abbreviations).
            
            OUTPUT FORMAT:
            Suggested prompt: "Your suggested prompt text here."
            
            Alternative prompt: "Alternative wording here."
            """;

    public ConversationDesignerAgent(ProviderManager providerManager) {
        super("conversation_designer", "Conversation Designer", "Generates prompt text for IVR nodes.", SYSTEM_INSTRUCTION, providerManager);
    }

    @Override
    protected String buildUserPrompt(String context) {
        return "Design IVR prompt text for the following node/context:\n\n" + context;
    }

    @Override
    protected String fallbackAdvice(String context) {
        return "[Conversation Designer] Could not generate prompts. Please describe the node purpose and target audience.";
    }
}
