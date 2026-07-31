package com.nexusivr.ai.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RefinedSpecCacheTest {

    @Test
    void testCachePutAndGet() {
        RefinedSpecCache.clear();
        String key = "create a bakery ivr";
        String value = "{\"business_domain\":\"restaurant\",\"departments\":[\"Orders\",\"Custom Cakes\"]}";

        RefinedSpecCache.put(key, value);
        String cached = RefinedSpecCache.get(key);

        assertNotNull(cached);
        assertEquals(value, cached);
    }

    @Test
    void testCacheMissReturnsNull() {
        RefinedSpecCache.clear();
        assertNull(RefinedSpecCache.get("nonexistent prompt"));
    }

    @Test
    void testCacheExpiry() throws InterruptedException {
        RefinedSpecCache.clear();
        String key = "expiring prompt";
        String value = "{\"business_domain\":\"generic\"}";

        RefinedSpecCache.put(key, value, 100); // 100ms TTL
        assertNotNull(RefinedSpecCache.get(key));

        Thread.sleep(150);
        assertNull(RefinedSpecCache.get(key));
    }

    @Test
    void testCacheNormalization() {
        RefinedSpecCache.clear();
        String key1 = "Create a Bakery IVR";
        String key2 = "create a bakery ivr";

        RefinedSpecCache.put(key1, "spec1");
        assertNotNull(RefinedSpecCache.get(key2));
    }
}
