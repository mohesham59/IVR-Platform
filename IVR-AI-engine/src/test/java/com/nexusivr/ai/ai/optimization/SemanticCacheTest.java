package com.nexusivr.ai.ai.optimization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SemanticCacheTest {

    @Test
    void testPutAndGet_returnsCachedValue() {
        SemanticCache cache = new SemanticCache(10, 60_000L);
        cache.put("key1", "response1");
        assertEquals("response1", cache.get("key1"));
    }

    @Test
    void testGet_missingKey_returnsNull() {
        SemanticCache cache = new SemanticCache(10, 60_000L);
        assertNull(cache.get("nonexistent"));
    }

    @Test
    void testInvalidate_removesEntry() {
        SemanticCache cache = new SemanticCache(10, 60_000L);
        cache.put("key1", "response1");
        cache.invalidate("key1");
        assertNull(cache.get("key1"));
    }

    @Test
    void testClear_removesAllEntries() {
        SemanticCache cache = new SemanticCache(10, 60_000L);
        cache.put("key1", "response1");
        cache.put("key2", "response2");
        cache.clear();
        assertNull(cache.get("key1"));
        assertNull(cache.get("key2"));
    }

    @Test
    void testSize_returnsCorrectCount() {
        SemanticCache cache = new SemanticCache(10, 60_000L);
        assertEquals(0, cache.size());
        cache.put("key1", "response1");
        assertEquals(1, cache.size());
    }

    @Test
    void testNormalize_keyIsCaseInsensitive() {
        SemanticCache cache = new SemanticCache(10, 60_000L);
        cache.put("Key1", "response1");
        assertEquals("response1", cache.get("key1"));
    }

    @Test
    void testSingletonInstance() {
        SemanticCache instance1 = SemanticCache.getInstance();
        SemanticCache instance2 = SemanticCache.getInstance();
        assertSame(instance1, instance2);
    }
}