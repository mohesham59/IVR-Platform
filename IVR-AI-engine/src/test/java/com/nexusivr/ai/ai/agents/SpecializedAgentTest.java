package com.nexusivr.ai.ai.agents;

import com.nexusivr.ai.ai.MockLlmClient;
import com.nexusivr.ai.ai.ProviderManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SpecializedAgentTest {

    @Test
    void testBusinessPlannerAgentAdvice() {
        ProviderManager pm = new ProviderManager();
        pm.setOverrideClient(new MockLlmClient());
        BusinessPlannerAgent agent = new BusinessPlannerAgent(pm);
        String advice = agent.advise("We are a hospital with emergency, appointments, and pharmacy departments.");
        assertNotNull(advice);
        assertFalse(advice.isBlank());
        assertFalse(advice.contains("<vxml"), "Business Planner must never return VoiceXML");
        assertFalse(advice.contains("FlowModel"), "Business Planner must never return FlowModel");
    }

    @Test
    void testConversationDesignerAgentAdvice() {
        ProviderManager pm = new ProviderManager();
        pm.setOverrideClient(new MockLlmClient());
        ConversationDesignerAgent agent = new ConversationDesignerAgent(pm);
        String advice = agent.advise("Main menu greeting for a bank");
        assertNotNull(advice);
        assertFalse(advice.isBlank());
        assertFalse(advice.contains("<vxml"), "Conversation Designer must never return VoiceXML");
        assertFalse(advice.contains("FlowModel"), "Conversation Designer must never return FlowModel");
    }

    @Test
    void testRoutingExpertAgentAdvice() {
        ProviderManager pm = new ProviderManager();
        pm.setOverrideClient(new MockLlmClient());
        RoutingExpertAgent agent = new RoutingExpertAgent(pm);
        String advice = agent.advise("Flow has a dead end at node n3 and no timeout handling.");
        assertNotNull(advice);
        assertFalse(advice.isBlank());
        assertFalse(advice.contains("<vxml"), "Routing Expert must never return VoiceXML");
        assertFalse(advice.contains("FlowModel"), "Routing Expert must never return FlowModel");
    }

    @Test
    void testVoicePromptWriterAgentAdvice() {
        ProviderManager pm = new ProviderManager();
        pm.setOverrideClient(new MockLlmClient());
        VoicePromptWriterAgent agent = new VoicePromptWriterAgent(pm);
        String advice = agent.advise("Write a prompt asking for a 10-digit account number");
        assertNotNull(advice);
        assertFalse(advice.isBlank());
        assertFalse(advice.contains("<vxml"), "Voice Prompt Writer must never return VoiceXML");
        assertFalse(advice.contains("FlowModel"), "Voice Prompt Writer must never return FlowModel");
    }

    @Test
    void testAllAgentsReturnTextOnly() {
        ProviderManager pm = new ProviderManager();
        pm.setOverrideClient(new MockLlmClient());

        SpecializedAgent[] agents = {
                new BusinessPlannerAgent(pm),
                new ConversationDesignerAgent(pm),
                new RoutingExpertAgent(pm),
                new VoicePromptWriterAgent(pm)
        };

        for (SpecializedAgent agent : agents) {
            String advice = agent.advise("Test context");
            assertNotNull(advice, agent.getId() + " returned null");
            assertFalse(advice.isBlank(), agent.getId() + " returned blank");
            assertFalse(advice.contains("<vxml"), agent.getId() + " returned VoiceXML");
            assertFalse(advice.contains("FlowModel"), agent.getId() + " returned FlowModel reference");
            assertFalse(advice.contains("<?xml"), agent.getId() + " returned XML");
        }
    }
}
