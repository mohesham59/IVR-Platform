package com.nexusivr.ai.service;

import com.nexusivr.ai.ai.AiResponse;
import com.nexusivr.ai.ai.ProviderManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PromptRefinerHardGateTest {

    private ProviderManager providerManager;
    private PromptRefinerService promptRefinerService;

    @BeforeEach
    void setUp() {
        providerManager = mock(ProviderManager.class);
        promptRefinerService = new PromptRefinerService(providerManager);
    }

    @Test
    void testValidBusinessLogicSpecAccepted() {
        String validSpec = "{\"refined_prompt\":\"Create a hotel IVR\",\"business_domain\":\"hotel\",\"departments\":[\"Reservations\",\"Front Desk\"],\"menu_options\":[\"Press 1 for Reservations\",\"Press 2 for Front Desk\"]}";
        AiResponse mockResp = mock(AiResponse.class);
        when(mockResp.isMock()).thenReturn(false);
        when(mockResp.getContent()).thenReturn(validSpec);
        when(providerManager.executeWithRetryAndFallback(anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(mockResp);

        String result = promptRefinerService.refine("Create a hotel IVR", "groq", "llama-3.3-70b-versatile", 0.7, 30);
        assertEquals(validSpec, result, "Valid business-logic spec must be accepted by Pass 1");
    }

    @Test
    void testMissingDepartmentsTriggersRetryAndFallsBackToRawPromptOnFailure() {
        // Spec missing departments array
        String invalidSpec = "{\"refined_prompt\":\"Create a hotel IVR\",\"business_domain\":\"hotel\"}";
        AiResponse mockResp = mock(AiResponse.class);
        when(mockResp.isMock()).thenReturn(false);
        when(mockResp.getContent()).thenReturn(invalidSpec);
        when(providerManager.executeWithRetryAndFallback(anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(mockResp);

        String rawPrompt = "Create a hotel IVR";
        String result = promptRefinerService.refine(rawPrompt, "groq", "llama-3.3-70b-versatile", 0.7, 30);

        // Must retry once, and when retry also returns invalid spec, fall back to rawPrompt
        verify(providerManager, times(2)).executeWithRetryAndFallback(anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString(), eq("PROMPT_REFINER"), anyList(), anyString(), eq(true));
        assertEquals(rawPrompt, result, "Stage 2 must receive original raw prompt when Pass 1 fails business-logic validation");
    }

    @Test
    void testBusinessLogicValidationRules() {
        assertTrue(PromptRefinerService.isValidRefinedJsonSpec("{\"business_domain\":\"healthcare\",\"departments\":[\"Emergency\"],\"menu_options\":[\"Press 1 for Emergency\"]}"));
        assertFalse(PromptRefinerService.isValidRefinedJsonSpec("{\"business_domain\":\"\",\"departments\":[\"Emergency\"],\"menu_options\":[\"Press 1 for Emergency\"]}"), "Empty domain is invalid");
        assertFalse(PromptRefinerService.isValidRefinedJsonSpec("{\"business_domain\":\"healthcare\",\"departments\":[],\"menu_options\":[\"Press 1 for Emergency\"]}"), "Empty departments is invalid");
        assertFalse(PromptRefinerService.isValidRefinedJsonSpec("{\"business_domain\":\"healthcare\",\"departments\":[\"Emergency\"],\"menu_options\":[]}"), "Empty menu_options is invalid");
    }
}
