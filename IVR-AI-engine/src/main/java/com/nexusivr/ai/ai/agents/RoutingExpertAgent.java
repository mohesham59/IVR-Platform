package com.nexusivr.ai.ai.agents;

import com.nexusivr.ai.ai.ProviderManager;
/**
 * Agent 3: Routing Expert
 * <p>
 * Recommends routing improvements for an existing flow.
 * Output: plain-text routing recommendations.
 * <b>Never</b> produces a FlowModel or VoiceXML.
 */
public class RoutingExpertAgent extends BaseSpecializedAgent {

    private static final String SYSTEM_INSTRUCTION = """
            You are a Routing Expert for IVR systems.
            Your ONLY job is to analyze an existing flow's routing and recommend improvements.
            
            CRITICAL RULES:
            - Return ONLY plain text recommendations. No JSON, no XML, no VoiceXML, no flow structure.
            - Never generate IVR flows, nodes, edges, or any graph structure.
            - Focus on: reducing caller effort, improving containment, adding timeouts, handling errors, balancing load.
            
            OUTPUT FORMAT:
            ## Routing Recommendations
            
            1. **Issue**: [description]
               **Recommendation**: [specific change]
               **Rationale**: [why this helps]
            
            2. ...
            """;

    public RoutingExpertAgent(ProviderManager providerManager) {
        super("routing_expert", "Routing Expert", "Recommends routing improvements for IVR flows.", SYSTEM_INSTRUCTION, providerManager);
    }

    @Override
    protected String buildUserPrompt(String context) {
        return "Analyze the following IVR flow routing and recommend improvements:\n\n" + context;
    }

    @Override
    protected String fallbackAdvice(String context) {
        return "[Routing Expert] Could not analyze routing. Please provide the current flow structure and specific concerns.";
    }
}
