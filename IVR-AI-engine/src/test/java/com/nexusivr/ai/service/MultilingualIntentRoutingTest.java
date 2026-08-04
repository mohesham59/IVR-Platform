package com.nexusivr.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@DisplayName("Multilingual Intent Routing Tests")
public class MultilingualIntentRoutingTest {

    private AiOperationRouter router;

    @BeforeEach
    void setUp() {
        UnifiedAiEngine unifiedAiEngine = mock(UnifiedAiEngine.class);
        ChatService chatService = mock(ChatService.class);
        router = new AiOperationRouter(unifiedAiEngine, chatService);
    }

    @Test
    @DisplayName("Should classify English flow-creation prompts as GENERATE_FLOW")
    void testEnglishFlowCreationIntent() {
        assertEquals(AiOperation.GENERATE_FLOW, router.classify("Create a clinic IVR with departments for Appointments, Pharmacy, Billing, and Triage"));
        assertEquals(AiOperation.GENERATE_FLOW, router.classify("Design a hotel concierge IVR system"));
        assertEquals(AiOperation.GENERATE_FLOW, router.classify("Build a pizza restaurant IVR flow"));
        assertEquals(AiOperation.GENERATE_FLOW, router.classify("Make a banking customer service IVR"));
        assertEquals(AiOperation.GENERATE_FLOW, router.classify("Generate an insurance claims processing flow"));
    }

    @Test
    @DisplayName("Should classify Arabic flow-creation prompts as GENERATE_FLOW")
    void testArabicFlowCreationIntent() {
        assertEquals(AiOperation.GENERATE_FLOW, router.classify("عاوز اعمل IVR لعياده مع أقسام المواعيد والصيدلية"));
        assertEquals(AiOperation.GENERATE_FLOW, router.classify("اعملي design بيه"));
        assertEquals(AiOperation.GENERATE_FLOW, router.classify("صمم نظام IVR لمطعم بيتزا"));
        assertEquals(AiOperation.GENERATE_FLOW, router.classify("أنشئ شجرة IVR لبنك"));
        assertEquals(AiOperation.GENERATE_FLOW, router.classify("بدي سوي ايفيار مستشفى"));
        assertEquals(AiOperation.GENERATE_FLOW, router.classify("أريد إنشاء فلو اتصالات"));
    }

    @Test
    @DisplayName("Should preserve non-generation queries correctly")
    void testNonGenerationQueries() {
        assertEquals(AiOperation.NODE_COUNT, router.classify("how many nodes?"));
        assertEquals(AiOperation.EXPORT_JSON, router.classify("give me the json code of this design"));
        assertEquals(AiOperation.VALIDATE_FLOW, router.classify("validate this flow"));
        assertEquals(AiOperation.CHAT, router.classify("hello how are you?"));
    }
}
