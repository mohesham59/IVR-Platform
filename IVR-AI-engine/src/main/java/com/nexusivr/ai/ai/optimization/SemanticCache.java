package com.nexusivr.ai.ai.optimization;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Semantic cache for LLM responses.
 * <p>
 * Caches responses by normalized prompt key to avoid redundant LLM calls.
 * Uses a bounded LRU eviction policy.
 * </p>
 */
public class SemanticCache {

    private static final SemanticCache INSTANCE = new SemanticCache();

    public static SemanticCache getInstance() {
        return INSTANCE;
    }

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final int maxSize;
    private final long defaultTtlMs;

    public SemanticCache() {
        this(1000, 24 * 60 * 60 * 1000L);
    }

    public SemanticCache(int maxSize, long defaultTtlMs) {
        this.maxSize = maxSize;
        this.defaultTtlMs = defaultTtlMs;
    }

    public static String buildKey(java.util.UUID tenantId, String promptOrSpec, String provider, String model, String domain) {
        String tenant = tenantId != null ? tenantId.toString() : "default_tenant";
        String base = promptOrSpec != null ? promptOrSpec.trim() : "";
        String prov = provider != null ? provider.trim().toLowerCase() : "";
        String m = model != null ? model.trim().toLowerCase() : "";
        String d = domain != null ? domain.trim().toLowerCase() : "";
        return "tenant=" + tenant + "|base=" + base + "|provider=" + prov + "|model=" + m + "|domain=" + d;
    }

    public String get(String key) {
        CacheEntry entry = cache.get(normalize(key));
        if (entry == null) return null;
        if (entry.isExpired()) {
            cache.remove(normalize(key));
            return null;
        }
        return entry.response;
    }

    public void put(String key, String response) {
        if (cache.size() >= maxSize) {
            evictOldest();
        }
        cache.put(normalize(key), new CacheEntry(response, System.currentTimeMillis() + defaultTtlMs));
    }

    public void invalidate(String key) {
        cache.remove(normalize(key));
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    private String normalize(String key) {
        if (key == null) return "";
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(key.trim().toLowerCase().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return key.trim().toLowerCase();
        }
    }

    private void evictOldest() {
        long oldest = Long.MAX_VALUE;
        String oldestKey = null;
        for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
            if (entry.getValue().expiresAt < oldest) {
                oldest = entry.getValue().expiresAt;
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) {
            cache.remove(oldestKey);
        }
    }

    private record CacheEntry(String response, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
