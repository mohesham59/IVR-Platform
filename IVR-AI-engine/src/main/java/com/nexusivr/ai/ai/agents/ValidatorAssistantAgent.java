package com.nexusivr.ai.ai.agents;

import com.nexusivr.ai.ai.ProviderManager;
/**
 * Agent 6: Validator Assistant
 * <p>
 * Explains validation issues in plain language and suggests fixes.
 * Output: human-readable explanations and fix recommendations.
 * <b>Never</b> produces a FlowModel or VoiceXML.
 */
public class ValidatorAssistantAgent extends BaseSpecializedAgent {

    private static final String SYSTEM_INSTRUCTION = """
            You are a Validator Assistant for IVR systems.
            Your ONLY job is to explain validation issues in plain language and suggest how to fix them.
            
            CRITICAL RULES:
            - Return ONLY plain text explanations. No JSON, no XML, no VoiceXML, no flow structure.
            - Never generate IVR flows, nodes, edges, or any graph structure.
            - Be educational: explain WHY the issue matters and HOW to fix it.
            - Use non-technical language where possible.
            
            OUTPUT FORMAT:
            ## Validation Issue Explanations
            
            ### Issue: [ISSUE_CODE]
            **What it means**: [plain-language explanation]
            **Why it matters**: [impact on callers/system]
            **How to fix**: [step-by-step fix instructions]
            
            ---
            
            ### Issue: [ISSUE_CODE]
            ...
            """;

    public ValidatorAssistantAgent(ProviderManager providerManager) {
        super("validator_assistant", "Validator Assistant", "Explains validation issues in plain language.", SYSTEM_INSTRUCTION, providerManager);
    }

    @Override
    protected String buildUserPrompt(String context) {
        return "Explain the following IVR validation issues and how to fix them:\n\n" + context;
    }

    @Override
    protected String fallbackAdvice(String context) {
        return "[Validator Assistant] Could not explain validation issues. Please provide the issue codes and messages.";
    }
}
