package com.nexusivr.ai.service;

import com.nexusivr.ai.ai.AiResponse;
import com.nexusivr.ai.ai.LlmClient;
import com.nexusivr.ai.ai.LlmProviderFactory;
import com.nexusivr.ai.ai.OllamaAiProvider;
import com.nexusivr.ai.ai.PromptBuilder;
import com.nexusivr.ai.ai.FunctionExecutor;
import com.nexusivr.ai.controller.ServiceRegistry;
import com.nexusivr.ai.model.Flow;
import com.nexusivr.ai.model.Message;
import com.nexusivr.ai.model.MessageRole;
import com.nexusivr.ai.service.exception.ProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("AiService Integration & Unit Tests")
public class AiServiceTest {

    private AiService aiService;
    private LlmClient mockLlmClient;

    @BeforeEach
    void setUp() {
        LlmProviderFactory.clearCache();
        mockLlmClient = mock(LlmClient.class);
        
        when(mockLlmClient.getProviderName()).thenReturn("groq");
        when(mockLlmClient.getModelName()).thenReturn("llama-3.3-70b-versatile");
        when(mockLlmClient.isAvailable()).thenReturn(true);
        
        // Mock generateResponse
        when(mockLlmClient.generateResponse(anyString(), anyString(), any()))
                .thenReturn(new AiResponse("Mocked Response containing NexusIVR Support", "llama-3.3-70b-versatile", 10, 10, false));
        when(mockLlmClient.generateResponse(anyString(), any()))
                .thenReturn(new AiResponse("Mocked Summary: Conversation consisted of 1 turns", "llama-3.3-70b-versatile", 10, 10, false));
        
        // Mock generateStructuredResponse
        when(mockLlmClient.generateStructuredResponse(anyString(), any()))
                .thenReturn(new AiResponse("{\"flowName\": \"Restaurant reservation IVR\", \"nodes\": [], \"edges\": []}", "llama-3.3-70b-versatile", 10, 10, false));
        when(mockLlmClient.generateStructuredResponse(eq(PromptBuilder.FLOW_GENERATOR_SYSTEM_INSTRUCTION), anyString(), any()))
                .thenReturn(new AiResponse("{\"flowName\": \"Restaurant reservation IVR\", \"nodes\": [], \"edges\": []}", "llama-3.3-70b-versatile", 10, 10, false));
        when(mockLlmClient.generateStructuredResponse(argThat(arg -> !PromptBuilder.FLOW_GENERATOR_SYSTEM_INSTRUCTION.equals(arg)), anyString(), any()))
                .thenReturn(new AiResponse("{\"improvedFlowName\": \"Improved IVR Flow\", \"changeLog\": [\"Optimize menu\"], \"rationale\": \"Mock rationale\"}", "llama-3.3-70b-versatile", 10, 10, false));

        LlmProviderFactory.setOverrideClient(mockLlmClient);
        aiService = new AiService();
    }

    @AfterEach
    void tearDown() {
        LlmProviderFactory.clearCache();
    }

    @Test
    @DisplayName("Should generate response, flow, and summary using default AI provider pipeline")
    void testAiServiceGenerations() {
        String response = aiService.generateResponse("Help me with my account", List.of());
        assertNotNull(response);
        assertTrue(response.contains("NexusIVR Support"));

        assertThrows(ProviderException.class, () -> aiService.generateFlow("Restaurant reservation IVR"),
                "generateFlow should throw ProviderException when all providers are unavailable (no silent template fallback)");

        Message msg = new Message();
        msg.setRole(MessageRole.USER);
        msg.setContent("I need help");
        String summary = aiService.summarizeConversation(List.of(msg));
        assertNotNull(summary);
        assertTrue(summary.contains("Conversation consisted of 1 turns"));
    }

    @Test
    @DisplayName("Should execute function calls via delegated FunctionExecutor")
    void testExecuteFunction() {
        String result = aiService.executeFunction("checkCustomerBalance", Map.of("customerId", "CUST-555"));
        assertNotNull(result);
        assertTrue(result.contains("CUST-555"));
        assertTrue(result.contains("250.75"));
    }

    @Test
    @DisplayName("Should properly wired components via dependency injection")
    void testDependencyInjection() {
        PromptBuilder customBuilder = new PromptBuilder();
        OllamaAiProvider customClient = new OllamaAiProvider();
        FunctionExecutor customExecutor = new FunctionExecutor();

        AiService customService = new AiService(customBuilder, customClient, customExecutor);

        assertSame(customBuilder, customService.getPromptBuilder());
        assertSame(customClient, customService.getAiProvider());
        assertSame(customExecutor, customService.getFunctionExecutor());
    }
}
