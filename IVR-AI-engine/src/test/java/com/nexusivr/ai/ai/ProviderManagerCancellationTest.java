package com.nexusivr.ai.ai;

import com.nexusivr.ai.service.GenerationCancellationRegistry;
import com.nexusivr.ai.service.exception.GenerationCancelledException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProviderManagerCancellationTest {

    private ProviderManager pm;

    @BeforeEach
    void setUp() {
        pm = new ProviderManager();
        pm.clearCache();
    }

    @Test
    void testExecutionFailsFastWhenCancelledInitially() {
        UUID sessionId = UUID.randomUUID();
        GenerationCancellationRegistry.cancel(sessionId);
        GenerationCancellationRegistry.setCurrentSessionId(sessionId);

        try {
            assertThrows(GenerationCancelledException.class, () ->
                    pm.executeWithRetryAndFallback(
                            "gemini", "gemini-2.0-flash", 0.7, 30,
                            "system instruction", "user prompt", "GENERATE_FLOW",
                            new ArrayList<>(), "banking", true
                    )
            );
        } finally {
            GenerationCancellationRegistry.clearCurrentSessionId();
        }
    }

    @Test
    void testCancellationRegistryClearAndReset() {
        UUID sessionId = UUID.randomUUID();
        GenerationCancellationRegistry.cancel(sessionId);
        assertTrue(GenerationCancellationRegistry.isCancelled(sessionId));

        GenerationCancellationRegistry.clear(sessionId);
        assertFalse(GenerationCancellationRegistry.isCancelled(sessionId));
    }
}
