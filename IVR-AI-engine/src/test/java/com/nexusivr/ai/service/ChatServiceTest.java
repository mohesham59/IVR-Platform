package com.nexusivr.ai.service;

import com.nexusivr.ai.ai.LlmProviderFactory;
import com.nexusivr.ai.dao.AiSessionDao;
import com.nexusivr.ai.dao.MessageDao;
import com.nexusivr.ai.dto.ChatResponse;
import com.nexusivr.ai.model.Message;
import com.nexusivr.ai.model.MessageRole;
import com.nexusivr.ai.model.flow.FlowModel;
import com.nexusivr.ai.model.flow.FlowNode;
import com.nexusivr.ai.model.flow.FlowNodeType;
import com.nexusivr.ai.model.flow.FlowConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ChatService Unit Tests")
public class ChatServiceTest {

    private ChatService chatService;
    private AiSessionDao sessionDao;
    private MessageDao messageDao;
    private AiService aiService;

    @BeforeEach
    void setUp() {
        sessionDao = mock(AiSessionDao.class);
        messageDao = mock(MessageDao.class);
        aiService = mock(AiService.class);
        when(aiService.generateResponse(anyString(), any())).thenReturn("Mocked AI response");
        when(aiService.generateResponse(anyString(), any(), anyString())).thenReturn("Mocked AI response");
        LlmProviderFactory.clearCache();
        chatService = new ChatService(sessionDao, messageDao, aiService);
        SessionMemoryStore.setModelToFlowRenderer(new ModelToFlowRenderer());
    }

    @AfterEach
    void tearDown() {
        SessionMemoryStore.clear(null);
        LlmProviderFactory.clearCache();
    }

    // ... existing tests ...

    @Test
    @DisplayName("Should return 'no flow' message when no flow is stored for session")
    void testNoFlowInSession() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId  = UUID.randomUUID();

        ChatResponse resp = chatService.sendMessage(sessionId, tenantId, "How many nodes?");
        assertNotNull(resp);
        assertTrue(resp.getReplyMessage().contains("no generated IVR flow"),
                "Expected 'no flow' message, got: " + resp.getReplyMessage());
    }

    @Test
    @DisplayName("Should answer node count deterministically from stored flow")
    void testNodeCountFromMemory() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId  = UUID.randomUUID();

        SessionMemoryStore.saveModel(sessionId, buildMockFlowModel());

        ChatResponse resp = chatService.sendMessage(sessionId, tenantId, "how many nodes?");
        assertNotNull(resp);
        assertEquals("The IVR flow contains 3 nodes.", resp.getReplyMessage());

        SessionMemoryStore.clear(sessionId);
    }

    @Test
    @DisplayName("Should answer edge count deterministically from stored flow")
    void testEdgeCountFromMemory() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId  = UUID.randomUUID();

        SessionMemoryStore.saveModel(sessionId, buildMockFlowModel());

        ChatResponse resp = chatService.sendMessage(sessionId, tenantId, "how many edges?");
        assertNotNull(resp);
        assertTrue(resp.getReplyMessage().contains("2"), "Edge count should be 2");

        SessionMemoryStore.clear(sessionId);
    }

    @Test
    @DisplayName("Should list all nodes from stored flow")
    void testListNodesFromMemory() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId  = UUID.randomUUID();

        SessionMemoryStore.saveModel(sessionId, buildMockFlowModel());

        ChatResponse resp = chatService.sendMessage(sessionId, tenantId, "list the nodes");
        assertNotNull(resp);
        assertTrue(resp.getReplyMessage().contains("Main Menu"),  "Should contain 'Main Menu'");
        assertTrue(resp.getReplyMessage().contains("Support Queue"), "Should contain 'Support Queue'");
        assertTrue(resp.getReplyMessage().contains("menu"),       "Should show node type 'menu'");

        SessionMemoryStore.clear(sessionId);
    }

    @Test
    @DisplayName("Should list queue nodes from stored flow")
    void testQueueNodesFromMemory() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId  = UUID.randomUUID();

        SessionMemoryStore.saveModel(sessionId, buildMockFlowModel());

        ChatResponse resp = chatService.sendMessage(sessionId, tenantId, "What queues exist?");
        assertNotNull(resp);
        assertTrue(resp.getReplyMessage().contains("Support Queue"),
                "Expected queue listing, got: " + resp.getReplyMessage());

        SessionMemoryStore.clear(sessionId);
    }

    @Test
    @DisplayName("Should count transfer nodes deterministically from stored flow")
    void testTransferCountFromMemory() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId  = UUID.randomUUID();

        SessionMemoryStore.saveModel(sessionId, buildMockFlowModel());

        ChatResponse resp = chatService.sendMessage(sessionId, tenantId, "How many transfers?");
        assertNotNull(resp);
        assertTrue(resp.getReplyMessage().contains("1"),
                "Transfer count should be 1, got: " + resp.getReplyMessage());

        SessionMemoryStore.clear(sessionId);
    }

    @Test
    @DisplayName("Should return flow summary when complexity is asked")
    void testFlowSummaryFromMemory() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId  = UUID.randomUUID();

        SessionMemoryStore.saveModel(sessionId, buildMockFlowModel());

        ChatResponse resp = chatService.sendMessage(sessionId, tenantId, "what's the complexity?");
        assertNotNull(resp);
        assertTrue(resp.getReplyMessage().contains("IVR Flow Summary"),
                "Expected flow summary, got: " + resp.getReplyMessage());

        SessionMemoryStore.clear(sessionId);
    }

    @Test
    @DisplayName("SessionMemoryStore: should correctly store and count nodes/edges from FlowModel")
    void testSessionMemoryStoreModelStorage() {
        UUID sessionId = UUID.randomUUID();
        SessionMemoryStore.saveModel(sessionId, buildMockFlowModel());

        SessionMemoryStore.SessionMemory memory = SessionMemoryStore.get(sessionId);
        assertNotNull(memory);
        assertEquals(3, memory.getNodeCount());
        assertEquals(2, memory.getEdgeCount());
        assertEquals(1, memory.countNodesByType("queue"));
        assertEquals(1, memory.countNodesByType("transfer"));
        assertEquals(1, memory.countNodesByType("menu"));

        SessionMemoryStore.clear(sessionId);
        assertNull(SessionMemoryStore.get(sessionId));
    }

    @Test
    @DisplayName("SessionMemoryStore: hasFlow returns false when no flow stored")
    void testHasFlowReturnsFalseWhenAbsent() {
        UUID sessionId = UUID.randomUUID();
        assertFalse(SessionMemoryStore.hasFlow(sessionId));
    }

    @Test
    @DisplayName("SessionMemoryStore: hasFlow returns true after saveModel")
    void testHasFlowReturnsTrueAfterSave() {
        UUID sessionId = UUID.randomUUID();
        SessionMemoryStore.saveModel(sessionId, buildMockFlowModel());
        assertTrue(SessionMemoryStore.hasFlow(sessionId));
        SessionMemoryStore.clear(sessionId);
    }

    @Test
    @DisplayName("Should sync flow via sentinel message (__flow_sync__) and verify it is not in message history")
    void testFlowSyncSentinel() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId  = UUID.randomUUID();
        FlowModel flowModel = buildMockFlowModel();
        SessionMemoryStore.saveModel(sessionId, flowModel);

        ChatResponse resp = chatService.sendMessage(sessionId, tenantId, "__flow_sync__:TestFlow:3",
                SessionMemoryStore.getFlowJson(sessionId));
        assertNotNull(resp);
        assertEquals("Flow synchronized.", resp.getReplyMessage());

        assertTrue(SessionMemoryStore.hasFlow(sessionId));
        assertEquals(3, SessionMemoryStore.get(sessionId).getNodeCount());

        long messageCount = messageDao.countMessages(sessionId, tenantId);
        assertEquals(0, messageCount, "Sync sentinel messages should not be persisted in chat history database");
    }

    @Test
    @DisplayName("Should answer node names deterministically when asked 'tell me node names'")
    void testTellMeNodeNames() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId  = UUID.randomUUID();

        SessionMemoryStore.saveModel(sessionId, buildMockFlowModel());

        ChatResponse resp = chatService.sendMessage(sessionId, tenantId, "tell me node names");
        assertNotNull(resp);
        assertTrue(resp.getReplyMessage().contains("Main Menu"));
        assertTrue(resp.getReplyMessage().contains("Support Queue"));
        assertTrue(resp.getReplyMessage().contains("Live Agent Transfer"));
    }

    @Test
    @DisplayName("Should answer flow summary deterministically when asked 'what is this flow?'")
    void testWhatIsThisFlow() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId  = UUID.randomUUID();

        SessionMemoryStore.saveModel(sessionId, buildMockFlowModel());

        ChatResponse resp = chatService.sendMessage(sessionId, tenantId, "what is this flow?");
        assertNotNull(resp);
        assertTrue(resp.getReplyMessage().contains("IVR Flow Summary"));
        assertTrue(resp.getReplyMessage().contains("Total Nodes: 3"));
    }

    @Test
    @DisplayName("Regression: Should verify informational questions do NOT change/regenerate active flow")
    void testRegressionInformationalQuestionsDoNotRegenerate() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId  = UUID.randomUUID();
        FlowModel originalFlow = buildMockFlowModel();

        SessionMemoryStore.saveModel(sessionId, originalFlow);

        String[] prompts = {
            "Is this the exact JSON currently used by the canvas?",
            "Is this simplified?",
            "Explain this design.",
            "Describe this flow.",
            "Validate this JSON.",
            "Review this IVR."
        };

        for (String prompt : prompts) {
            chatService.sendMessage(sessionId, tenantId, prompt);

            // Assert that the active flow model remains unchanged
            com.nexusivr.ai.model.flow.FlowModel currentModel = SessionMemoryStore.getFlowModel(sessionId);
            assertSame(originalFlow, currentModel, "Flow model should not be modified/regenerated by informational question: " + prompt);
        }
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private FlowModel buildMockFlowModel() {
        FlowModel model = new FlowModel();
        model.setId("flow-1");
        model.setName("Test Flow");
        model.setDescription("Mock flow for testing");

        FlowNode n1 = new FlowNode("n1", FlowNodeType.MENU, "Main Menu");
        n1.setSubtitle("Main Menu");

        FlowNode n2 = new FlowNode("n2", FlowNodeType.QUEUE, "Support Queue");
        n2.setSubtitle("Support Queue");

        FlowNode n3 = new FlowNode("n3", FlowNodeType.TRANSFER, "Live Agent Transfer");
        n3.setSubtitle("Transfer");

        model.addNode(n1);
        model.addNode(n2);
        model.addNode(n3);

        model.addConnection(new FlowConnection("e1", "n1", "out", "n2", "in"));
        model.addConnection(new FlowConnection("e2", "n2", "answered", "n3", "in"));

        return model;
    }
}
