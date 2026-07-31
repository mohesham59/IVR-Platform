package com.nexusivr.ai.service;

import com.nexusivr.ai.ai.PromptBuilder;
import com.nexusivr.ai.ai.ProviderManager;
import com.nexusivr.ai.ai.TemplateGenerator;
import com.nexusivr.ai.model.Flow;
import com.nexusivr.ai.model.Message;
import com.nexusivr.ai.model.MessageRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CleanFallbackGreetingTest {

    private ProviderManager providerManager;
    private PromptRefinerService promptRefinerService;
    private UnifiedAiEngine unifiedAiEngine;
    private final List<String> capturedDomainsForGenerateFlow = new ArrayList<>();

    @BeforeEach
    void setUp() {
        providerManager = mock(ProviderManager.class);
        when(providerManager.isProviderAvailable(anyString())).thenReturn(false);
        capturedDomainsForGenerateFlow.clear();

        // When executeWithRetryAndFallback is called:
        // Pass 1 ("PROMPT_REFINER") -> returns null (skip Pass 1)
        // Pass 2 ("GENERATE_FLOW") -> invokes TemplateGenerator circuit breaker with passed domain
        when(providerManager.executeWithRetryAndFallback(anyString(), anyString(), anyDouble(), anyInt(),
                anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean()))
                .thenAnswer(invocation -> {
                    String callerLabel = invocation.getArgument(6);
                    String userPrompt = invocation.getArgument(5);
                    String domain = invocation.getArgument(8);

                    if ("PROMPT_REFINER".equalsIgnoreCase(callerLabel)) {
                        return null; // Skip refinement fallback
                    } else {
                        capturedDomainsForGenerateFlow.add(domain);
                        // Pass 2: Invoke TemplateGenerator circuit breaker
                        TemplateGenerator generator = new TemplateGenerator();
                        return generator.generateStructuredResponse(null, userPrompt, List.of(), domain);
                    }
                });

        promptRefinerService = new PromptRefinerService(providerManager);
        unifiedAiEngine = new UnifiedAiEngine(providerManager, new PromptBuilder(), new FlowContextService(), promptRefinerService);
    }

    @Test
    void testBothPassesFailingProducesCleanGreetingWithoutCorruptedHeadersOrLeakedKeys() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String userPrompt = "design technical support IVR with L1 and L2 helpdesk";
        
        Message historyMsg = new Message();
        historyMsg.setSessionId(sessionId);
        historyMsg.setTenantId(tenantId);
        historyMsg.setRole(MessageRole.USER);
        historyMsg.setContent("Recent conversation context:\nUSER: design technical support IVR");

        // Execute flow generation with autoRefine=true and prompt history
        Flow flow = unifiedAiEngine.generateFlow(tenantId, sessionId, userPrompt, "groq", "llama-3.3-70b-versatile", 0.7, 30, List.of(historyMsg), true);

        assertNotNull(flow);
        assertNotNull(flow.getFlowJson());
        String flowJson = flow.getFlowJson();

        // Assertions: Greeting text must be clean and domain-relevant
        assertFalse(flowJson.toLowerCase().contains("welcome to welcome to"), "Greeting must not contain duplicate 'Welcome to Welcome to'");
        assertFalse(flowJson.contains("Recent conversation context"), "Greeting must not leak 'Recent conversation context' text");
        assertFalse(flowJson.contains("refined_prompt"), "Greeting must not leak JSON key name 'refined_prompt'");
        assertFalse(flowJson.contains("business_domain"), "Greeting must not leak JSON key name 'business_domain'");

        // Assert clean domain content is present
        assertTrue(flowJson.toLowerCase().contains("technical support") || flowJson.toLowerCase().contains("welcome to"),
                "Greeting must contain clean domain-relevant content");
    }

    @Test
    void testDetectedDomainPropagatesToTemplateGeneratorFallback() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String userPrompt = "design the clinic IVR for appointments and patient care";

        Flow flow = unifiedAiEngine.generateFlow(tenantId, sessionId, userPrompt, "groq", "llama-3.3-70b-versatile", 0.7, 30, List.of(), true);

        assertNotNull(flow);
        assertFalse(capturedDomainsForGenerateFlow.isEmpty(), "GenerateFlow must call executeWithRetryAndFallback");
        String lastDomain = capturedDomainsForGenerateFlow.get(capturedDomainsForGenerateFlow.size() - 1);
        assertEquals("healthcare", lastDomain, "TemplateGenerator fallback must receive the early-detected domain ('healthcare'), not reset to 'generic'");
    }
}
