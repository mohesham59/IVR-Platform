package com.nexusivr.ai.ai.optimization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptMinimizerTest {

    @Test
    void testMinimizeUserPrompt_trimsWhitespace() {
        String result = PromptMinimizer.minimizeUserPrompt("  Hello world  ");
        assertEquals("Hello world", result);
    }

    @Test
    void testMinimizeUserPrompt_nullInput_returnsEmpty() {
        String result = PromptMinimizer.minimizeUserPrompt(null);
        assertEquals("", result);
    }

    @Test
    void testMinimizeUserPrompt_blankInput_returnsEmpty() {
        String result = PromptMinimizer.minimizeUserPrompt("   ");
        assertEquals("", result);
    }

    @Test
    void testMinimizeSystemPrompt_removesVerboseVoiceXml() {
        String input = "System prompt with VoiceXML 2.1 and <vxml version=\"2.1\">some content</vxml> more text";
        String result = PromptMinimizer.minimizeSystemPrompt(input);
        assertFalse(result.contains("<vxml"), "VoiceXML should be removed from system prompt");
    }

    @Test
    void testReplaceVoiceXmlWithSummary_largeVoiceXml_replacesWithSummary() {
        StringBuilder largeVoiceXml = new StringBuilder("<vxml version=\"2.1\">\n");
        for (int i = 0; i < 60; i++) {
            largeVoiceXml.append("  <form id=\"f").append(i).append("\">\n");
            largeVoiceXml.append("    <block>\n");
            largeVoiceXml.append("      <prompt>Hello</prompt>\n");
            largeVoiceXml.append("    </block>\n");
            largeVoiceXml.append("  </form>\n");
        }
        largeVoiceXml.append("</vxml>");
        String prompt = "Improve this flow";
        String result = PromptMinimizer.replaceVoiceXmlWithSummary(prompt, largeVoiceXml.toString());
        assertTrue(result.contains("[VoiceXML:"));
    }

    @Test
    void testReplaceVoiceXmlWithSummary_smallVoiceXml_returnsOriginal() {
        String prompt = "Improve this flow";
        String voiceXml = "<vxml version=\"2.1\">\n  <form id=\"f1\">\n    <block>\n      <prompt>Hello</prompt>\n    </block>\n  </form>\n</vxml>";
        String result = PromptMinimizer.replaceVoiceXmlWithSummary(prompt, voiceXml);
        assertEquals(prompt, result);
    }

    @Test
    void testReplaceVoiceXmlWithSummary_nullVoiceXml_returnsOriginal() {
        String prompt = "Improve this flow";
        String result = PromptMinimizer.replaceVoiceXmlWithSummary(prompt, null);
        assertEquals(prompt, result);
    }

    @Test
    void testReplaceFlowJsonWithSummary_addsSummaryMarker() {
        String prompt = "Improve this flow";
        String flowJson = "{\"nodes\":[{\"id\":\"n1\"}],\"edges\":[]}";
        String result = PromptMinimizer.replaceFlowJsonWithSummary(prompt, flowJson);
        assertTrue(result.contains("[Flow context provided as compact summary"));
    }

    @Test
    void testReplaceFlowJsonWithSummary_nullFlowJson_returnsOriginal() {
        String prompt = "Improve this flow";
        String result = PromptMinimizer.replaceFlowJsonWithSummary(prompt, null);
        assertEquals(prompt, result);
    }
}