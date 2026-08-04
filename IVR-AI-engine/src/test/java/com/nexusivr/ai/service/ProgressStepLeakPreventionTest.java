package com.nexusivr.ai.service;

import com.nexusivr.ai.dao.AiSessionDao;
import com.nexusivr.ai.dao.MessageDao;
import com.nexusivr.ai.dto.ChatResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Progress Step Leak Prevention Tests")
public class ProgressStepLeakPreventionTest {

    private ChatService chatService;
    private FlowContextService flowContextService;
    private AiSessionDao sessionDao;
    private MessageDao messageDao;
    private AiService aiService;
    private AiOperationRouter router;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        flowContextService = mock(FlowContextService.class);
        sessionDao = mock(AiSessionDao.class);
        messageDao = mock(MessageDao.class);
        aiService = mock(AiService.class);
        router = mock(AiOperationRouter.class);

        chatService = new ChatService(sessionDao, messageDao, aiService, flowContextService);
    }

    @Test
    @DisplayName("Should intercept internal UI progress step labels as user messages")
    void testProgressStepLabelInterception() {
        String[] stageLabels = {
            "Converting to nodes",
            "converting to nodes",
            "Generating VXML",
            "Understanding request",
            "Business analysis",
            "Planning flow",
            "Selecting template/domain",
            "Validating",
            "Rendering canvas",
            "converting"
        };

        for (String label : stageLabels) {
            ChatResponse response = chatService.sendMessage(sessionId, tenantId, label, "CHAT", null);

            assertNotNull(response);
            assertTrue(response.getReplyMessage().contains("Ignored progress step message"),
                    "Stage label '" + label + "' should be intercepted and ignored");

            // Verify that the router was NEVER called for progress labels
            verify(router, never()).classify(label);
        }
    }

    @Test
    @DisplayName("Should allow legitimate user chat messages")
    void testLegitimateChatMessage() {
        when(aiService.generateResponse(anyString(), any())).thenReturn("Here is information about IVR.");

        ChatResponse response = chatService.sendMessage(sessionId, tenantId, "Tell me about IVR best practices", "CHAT", null);

        assertNotNull(response);
        assertFalse(response.getReplyMessage().contains("Ignored progress step message"));
    }
}
