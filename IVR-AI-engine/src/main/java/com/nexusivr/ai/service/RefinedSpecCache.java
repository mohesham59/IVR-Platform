package com.nexusivr.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory cache for Pass 1 refined specifications.
 *
 * <p>Key: composite string (tenantId, prompt/spec, autoRefine, provider, model, domain).
 * Value: refined spec string.
 * TTL: configurable, defaults to 2 hours.
 */
public class RefinedSpecCache {

    private static final Logger logger = LoggerFactory.getLogger(RefinedSpecCache.class);

    private static final long DEFAULT_TTL_MS = 2 * 60 * 60 * 1000; // 2 hours

    private static final Map<String, CachedEntry> cache = new ConcurrentHashMap<>();

    private RefinedSpecCache() {}

    public static String buildKey(java.util.UUID tenantId, String promptOrSpec, boolean autoRefine, String provider, String model, String domain) {
        String tenant = tenantId != null ? tenantId.toString() : "default_tenant";
        String base = promptOrSpec != null ? promptOrSpec.trim() : "";
        String prov = provider != null ? provider.trim().toLowerCase() : "";
        String m = model != null ? model.trim().toLowerCase() : "";
        String d = domain != null ? domain.trim().toLowerCase() : "";
        return "tenant=" + tenant + "|base=" + base + "|autoRefine=" + autoRefine + "|provider=" + prov + "|model=" + m + "|domain=" + d;
    }

    public static String get(String normalizedPrompt) {
        String key = normalize(normalizedPrompt);
        CachedEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() - entry.timestampMs > entry.ttlMs) {
            cache.remove(key, entry);
            return null;
        }
        logger.debug("[RefinedSpecCache] Cache hit for prompt hash={}", hash(key));
        return entry.value;
    }

    public static void put(String normalizedPrompt, String refinedSpec, long ttlMs) {
        String key = normalize(normalizedPrompt);
        long ttl = ttlMs > 0 ? ttlMs : DEFAULT_TTL_MS;
        cache.put(key, new CachedEntry(refinedSpec, System.currentTimeMillis(), ttl));
        logger.debug("[RefinedSpecCache] Cache miss, stored refined spec for prompt hash={}", hash(key));
    }

    public static void put(String normalizedPrompt, String refinedSpec) {
        put(normalizedPrompt, refinedSpec, DEFAULT_TTL_MS);
    }

    public static void clear() {
        cache.clear();
    }

    private static String hash(String prompt) {
        if (prompt == null) return "null";
        return Integer.toHexString(prompt.hashCode());
    }

    private static String normalize(String key) {
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

    private record CachedEntry(String value, long timestampMs, long ttlMs) {}
}
