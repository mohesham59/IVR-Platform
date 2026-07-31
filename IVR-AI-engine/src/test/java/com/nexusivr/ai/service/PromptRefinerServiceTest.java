package com.nexusivr.ai.service;

import com.nexusivr.ai.ai.AiResponse;
import com.nexusivr.ai.ai.ProviderManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PromptRefinerServiceTest {

    private ProviderManager providerManager;
    private PromptRefinerService refinerService;

    @BeforeEach
    void setUp() {
        providerManager = mock(ProviderManager.class);
        refinerService = new PromptRefinerService(providerManager);
        RefinedSpecCache.clear();
    }

    @Test
    void testRefinerReturnsStructuredSpec() {
        AiResponse mockResponse = mock(AiResponse.class);
        when(mockResponse.isMock()).thenReturn(false);
        when(mockResponse.getContent()).thenReturn("{\"refined_prompt\":\"A restaurant IVR with orders and reservations\",\"business_domain\":\"restaurant\",\"departments\":[\"Orders\",\"Reservations\",\"Hostess\"],\"menu_options\":[\"Press 1 for Orders\",\"Press 2 for Reservations\",\"Press 0 for Hostess\"],\"greeting\":\"Welcome\",\"closing\":\"Goodbye\",\"business_hours\":null,\"transfers\":[],\"error_handling\":\"Repeat then agent\",\"authentication\":null,\"tone\":\"friendly\"}");
        when(providerManager.executeWithRetryAndFallback(anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(mockResponse);

        String result = refinerService.refine("Create a pizza restaurant IVR", "gemini", "gemini-2.0-flash", 0.7, 30);

        assertTrue(result.contains("restaurant"));
        assertTrue(result.contains("Orders"));
        verify(providerManager, times(1)).executeWithRetryAndFallback(anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString(), eq("PROMPT_REFINER"), anyList(), anyString(), eq(true));
    }

    @Test
    void testRefinerFallsBackToRawPromptOnEmptyResponse() {
        AiResponse mockResponse = mock(AiResponse.class);
        when(mockResponse.isMock()).thenReturn(false);
        when(mockResponse.getContent()).thenReturn("");
        when(providerManager.executeWithRetryAndFallback(anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(mockResponse);

        String result = refinerService.refine("Create a pizza restaurant IVR", "gemini", "gemini-2.0-flash", 0.7, 30);

        assertEquals("Create a pizza restaurant IVR", result);
    }

    @Test
    void testRefinerReturnsRawPromptOnMockOrNullResponse() {
        AiResponse mockResponse = mock(AiResponse.class);
        when(mockResponse.isMock()).thenReturn(true);
        when(providerManager.executeWithRetryAndFallback(anyString(), anyString(), anyDouble(), anyInt(), anyString(), anyString(), anyString(), anyList(), anyString(), anyBoolean()))
                .thenReturn(mockResponse);

        String result = refinerService.refine("Create a pizza restaurant IVR", "gemini", "gemini-2.0-flash", 0.7, 30);
        assertEquals("Create a pizza restaurant IVR", result);
    }
}
