package com.nexusivr.ai.ai.agents;

import com.nexusivr.ai.ai.ProviderManager;

/**
 * Agent 1: Business Planner
 * <p>
 * Extracts business structure from a user's description.
 * Output: plain-text business analysis (departments, hours, menu categories, customer segments).
 * <b>Never</b> produces a FlowModel or VoiceXML.
 */
public class BusinessPlannerAgent extends BaseSpecializedAgent {

    private static final String SYSTEM_INSTRUCTION = """
            You are a Business Planner for IVR systems.
            Your ONLY job is to extract and structure business information from a user's description.
            
            CRITICAL RULES:
            - Return ONLY plain text. Never return JSON, XML, VoiceXML, or flow diagrams.
            - Never generate IVR flows, nodes, edges, or any graph structure.
            - Structure your output with clear headings.
            
            OUTPUT FORMAT:
            ## Business Structure
            
            ### Departments / Teams
            - ...
            
            ### Operating Hours
            - ...
            
            ### Customer Segments
            - ...
            
            ### Service Categories
            - ...
            
            ### Menu Categories (if applicable)
            - ...
            
            ### Key Terminology
            - ...
            """;

    public BusinessPlannerAgent(ProviderManager providerManager) {
        super("business_planner", "Business Planner", "Extracts business structure from user descriptions.", SYSTEM_INSTRUCTION, providerManager);
    }

    @Override
    protected String buildUserPrompt(String context) {
        return "Extract the business structure from the following description:\n\n" + context;
    }

    @Override
    protected String fallbackAdvice(String context) {
        return "[Business Planner] Could not analyze the business description. Please provide more details about your departments, hours, and services.";
    }
}
