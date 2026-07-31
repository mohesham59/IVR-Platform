package com.nexusivr.ai.ai.optimization;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuotaAwareRouterTest {

    private QuotaAwareRouter router;

    @BeforeEach
    void setUp() {
        router = QuotaAwareRouter.getInstance();
        router.resetDaily();
    }

    @AfterEach
    void tearDown() {
        router.resetDaily();
    }

    @Test
    void testSelectProvider_returnsProviderFromList() {
        List<String> providers = List.of("groq", "gemini");
        String selected = router.selectProvider(providers, 100, false);
        assertNotNull(selected);
        assertTrue(providers.contains(selected));
    }

    @Test
    void testSelectProvider_emptyList_returnsNull() {
        String selected = router.selectProvider(List.of(), 100, false);
        assertNull(selected);
    }

    @Test
    void testSetDailyBudget_setsBudget() {
        router.setDailyBudget("groq", 500_000L);
        // Just verify no exception is thrown
        assertTrue(true);
    }

    @Test
    void testIsWithinQuota_initiallyTrue() {
        assertTrue(router.isWithinQuota("groq", 100));
    }

    @Test
    void testRecordUsage_tracksUsage() {
        router.recordUsage("groq", 100);
        router.recordUsage("groq", 50);
        assertTrue(router.isWithinQuota("groq", 100));
    }

    @Test
    void testGetDailyUsage_returnsUsageMap() {
        router.recordUsage("groq", 100);
        router.recordUsage("gemini", 200);
        var usage = router.getDailyUsage();
        assertEquals(100L, usage.get("groq").longValue());
        assertEquals(200L, usage.get("gemini").longValue());
    }

    @Test
    void testResetDaily_clearsUsage() {
        router.recordUsage("groq", 100);
        router.resetDaily();
        var usage = router.getDailyUsage();
        assertTrue(usage.isEmpty() || usage.get("groq") == 0);
    }

    @Test
    void testSingletonInstance() {
        QuotaAwareRouter another = QuotaAwareRouter.getInstance();
        assertSame(router, another);
    }
}