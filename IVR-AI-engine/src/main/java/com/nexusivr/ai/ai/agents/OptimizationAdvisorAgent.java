package com.nexusivr.ai.ai.agents;

import com.nexusivr.ai.ai.ProviderManager;
/**
 * Agent 5: Optimization Advisor
 * <p>
 * Suggests improvements for an existing flow based on validation issues and metrics.
 * Output: plain-text prioritized improvement recommendations.
 * <b>Never</b> produces a FlowModel or VoiceXML.
 */
public class OptimizationAdvisorAgent extends BaseSpecializedAgent {

    private static final String SYSTEM_INSTRUCTION = """
            You are an Optimization Advisor for IVR systems.
            Your ONLY job is to analyze validation issues and metrics, then suggest prioritized improvements.
            
            CRITICAL RULES:
            - Return ONLY plain text recommendations. No JSON, no XML, no VoiceXML, no flow structure.
            - Never generate IVR flows, nodes, edges, or any graph structure.
            - Prioritize by impact: high (critical blockers), medium (usability), low (polish).
            - Be specific and actionable.
            
            OUTPUT FORMAT:
            ## Optimization Recommendations
            
            ### High Priority
            1. **Issue**: [description]
               **Fix**: [specific actionable fix]
               **Impact**: [expected improvement]
            
            ### Medium Priority
            1. ...
            
            ### Low Priority
            1. ...
            """;

    public OptimizationAdvisorAgent(ProviderManager providerManager) {
        super("optimization_advisor", "Optimization Advisor", "Suggests improvements based on validation issues and metrics.", SYSTEM_INSTRUCTION, providerManager);
    }

    @Override
    protected String buildUserPrompt(String context) {
        return "Analyze the following IVR flow issues and suggest optimizations:\n\n" + context;
    }

    @Override
    protected String fallbackAdvice(String context) {
        return "[Optimization Advisor] Could not generate optimization advice. Please provide validation issues and flow metrics.";
    }
}
