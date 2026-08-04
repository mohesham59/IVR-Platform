package com.nexusivr.ai.ai.agents;

import com.nexusivr.ai.ai.ProviderManager;
/**
 * Agent 4: Voice Prompt Writer
 * <p>
 * Generates professional, TTS-ready voice prompt text.
 * Output: polished prompt text.
 * <b>Never</b> produces a FlowModel or VoiceXML.
 */
public class VoicePromptWriterAgent extends BaseSpecializedAgent {

    private static final String SYSTEM_INSTRUCTION = """
            You are a professional Voice Prompt Writer for IVR systems.
            Your ONLY job is to write polished, caller-friendly, TTS-ready prompt text.
            
            CRITICAL RULES:
            - Return ONLY the prompt text. No JSON, no XML, no VoiceXML, no flow structure.
            - Never generate IVR flows, nodes, edges, or any graph structure.
            - Use natural, conversational language.
            - Avoid jargon, abbreviations, and special characters that TTS may mispronounce.
            - Keep prompts under 200 characters when possible.
            - Include a clear call-to-action.
            
            OUTPUT FORMAT:
            Primary prompt: "Your polished prompt text here."
            
            Alternative: "Alternative wording here."
            """;

    public VoicePromptWriterAgent(ProviderManager providerManager) {
        super("voice_prompt_writer", "Voice Prompt Writer", "Generates professional, TTS-ready voice prompts.", SYSTEM_INSTRUCTION, providerManager);
    }

    @Override
    protected String buildUserPrompt(String context) {
        return "Write a professional IVR voice prompt for:\n\n" + context;
    }

    @Override
    protected String fallbackAdvice(String context) {
        return "[Voice Prompt Writer] Could not generate prompt. Please describe what the caller needs to hear.";
    }
}
