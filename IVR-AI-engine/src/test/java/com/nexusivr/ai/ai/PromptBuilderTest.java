package com.nexusivr.ai.ai;

import com.nexusivr.ai.model.Message;
import com.nexusivr.ai.model.MessageRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PromptBuilder Unit Tests")
public class PromptBuilderTest {

    private PromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        promptBuilder = new PromptBuilder();
    }

    @Test
    @DisplayName("Should build chat prompt with transcript history and user turn")
    void testBuildChatPrompt() {
        Message msg1 = new Message();
        msg1.setRole(MessageRole.USER);
        msg1.setContent("Hello");

        Message msg2 = new Message();
        msg2.setRole(MessageRole.ASSISTANT);
        msg2.setContent("Hi, how can I help you?");

        String chatPrompt = promptBuilder.buildChatPrompt("I need to transfer my call", List.of(msg1, msg2));
        assertNotNull(chatPrompt);
        assertTrue(chatPrompt.contains("USER: Hello"));
        assertTrue(chatPrompt.contains("ASSISTANT: Hi, how can I help you?"));
        assertTrue(chatPrompt.contains("I need to transfer my call"));
    }

    @Test
    @DisplayName("Should build flow generation prompt containing only business description")
    void testBuildFlowGenerationPrompt() {
        String prompt = promptBuilder.buildFlowGenerationPrompt("Healthcare appointment booking system");
        assertNotNull(prompt);
        assertTrue(prompt.contains("Healthcare appointment booking system"));
        assertFalse(prompt.contains("VoiceXML 2.1"));
        assertFalse(prompt.contains("SUPPORTED"));
        assertFalse(prompt.contains("IVR PLAN"));
    }

    @Test
    @DisplayName("Should build summarization prompt for conversation history")
    void testBuildSummarizationPrompt() {
        Message msg = new Message();
        msg.setRole(MessageRole.USER);
        msg.setContent("I want to check my account balance");

        String prompt = promptBuilder.buildSummarizationPrompt(List.of(msg));
        assertNotNull(prompt);
        assertTrue(prompt.contains("I want to check my account balance"));
        assertTrue(prompt.contains("Extract key topics"));

        String emptyPrompt = promptBuilder.buildSummarizationPrompt(List.of());
        assertTrue(emptyPrompt.contains("[No messages recorded]"));
    }
}
