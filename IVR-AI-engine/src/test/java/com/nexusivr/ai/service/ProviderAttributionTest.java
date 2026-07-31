package com.nexusivr.ai.service;

import com.nexusivr.ai.dto.common.ErrorResponse;
import com.nexusivr.ai.model.Flow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProviderAttributionTest {

    @Test
    void testFlowSetsFallbackFieldsWhenProviderDiffers() {
        Flow flow = new Flow();
        flow.setSelectedProvider("Gemini");
        flow.setActualProviderUsed("Groq");
        flow.setFallbackUsed(true);
        flow.setFallbackReason("Gemini failed. Response generated using Groq.");

        assertEquals("Gemini", flow.getSelectedProvider());
        assertEquals("Groq", flow.getActualProviderUsed());
        assertTrue(flow.isFallbackUsed());
        assertEquals("Gemini failed. Response generated using Groq.", flow.getFallbackReason());
    }

    @Test
    void testFlowDoesNotSetFallbackWhenSameProvider() {
        Flow flow = new Flow();
        flow.setSelectedProvider("Groq");
        flow.setActualProviderUsed("Groq");
        flow.setFallbackUsed(false);
        flow.setFallbackReason("");

        assertEquals("Groq", flow.getSelectedProvider());
        assertEquals("Groq", flow.getActualProviderUsed());
        assertFalse(flow.isFallbackUsed());
        assertEquals("", flow.getFallbackReason());
    }

    @Test
    void testErrorResponseSetsFallbackFieldsWhenProviderDiffers() {
        ErrorResponse error = new ErrorResponse();
        error.setSelectedProvider("Gemini");
        error.setActualProviderUsed("Groq");
        error.setFallbackUsed(true);
        error.setFallbackReason("Gemini failed. Response generated using Groq.");

        assertEquals("Gemini", error.getSelectedProvider());
        assertEquals("Groq", error.getActualProviderUsed());
        assertTrue(error.isFallbackUsed());
        assertEquals("Gemini failed. Response generated using Groq.", error.getFallbackReason());
    }

    @Test
    void testErrorResponseDoesNotSetFallbackWhenSameProvider() {
        ErrorResponse error = new ErrorResponse();
        error.setSelectedProvider("Groq");
        error.setActualProviderUsed("Groq");
        error.setFallbackUsed(false);
        error.setFallbackReason("");

        assertEquals("Groq", error.getSelectedProvider());
        assertEquals("Groq", error.getActualProviderUsed());
        assertFalse(error.isFallbackUsed());
        assertEquals("", error.getFallbackReason());
    }
}
