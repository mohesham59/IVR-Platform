package com.nexusivr.ai.service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory thread-safe cancellation registry to track cancelled AI generation requests.
 * Uses a ThreadLocal to propagate the current request's session ID without changing method signatures.
 */
public class GenerationCancellationRegistry {

    private static final ConcurrentHashMap<String, AtomicBoolean> cancelledSessions = new ConcurrentHashMap<>();
    private static final ThreadLocal<UUID> currentSessionId = new ThreadLocal<>();

    public static void setCurrentSessionId(UUID sessionId) {
        currentSessionId.set(sessionId);
    }

    public static UUID getCurrentSessionId() {
        return currentSessionId.get();
    }

    public static void clearCurrentSessionId() {
        currentSessionId.remove();
    }

    public static void cancel(String sessionId) {
        if (sessionId != null) {
            cancelledSessions.computeIfAbsent(sessionId, k -> new AtomicBoolean(false)).set(true);
        }
    }

    public static void cancel(UUID sessionId) {
        if (sessionId != null) {
            cancel(sessionId.toString());
        }
    }

    public static boolean isCancelled(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        AtomicBoolean cancelled = cancelledSessions.get(sessionId);
        return cancelled != null && cancelled.get();
    }

    public static boolean isCancelled(UUID sessionId) {
        if (sessionId == null) {
            return false;
        }
        return isCancelled(sessionId.toString());
    }

    public static void clear(String sessionId) {
        if (sessionId != null) {
            cancelledSessions.remove(sessionId);
        }
    }

    public static void clear(UUID sessionId) {
        if (sessionId != null) {
            clear(sessionId.toString());
        }
    }
}
