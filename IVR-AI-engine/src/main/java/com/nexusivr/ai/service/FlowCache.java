package com.nexusivr.ai.service;

import com.nexusivr.ai.model.flow.FlowModel;

import java.util.UUID;

/**
 * Backward-compatible facade over {@link SessionMemoryStore}.
 *
 * <p>New code should use {@link SessionMemoryStore} directly for richer session memory.
 * This class keeps the old {@code FlowCache} API working so existing callers
 * (servlets, tests) do not need to change.
 */
public class FlowCache {

    private FlowCache() {}

    /**
     * Saves the FlowModel in the session-isolated memory store.
     *
     * @param sessionId UUID of the active chat session
     * @param flowModel the Internal Flow Model to store
     */
    public static void saveFlow(UUID sessionId, FlowModel flowModel) {
        SessionMemoryStore.saveModel(sessionId, flowModel);
    }

    /**
     * Returns the stored flow as React Flow JSON for a session, or {@code null} if absent.
     *
     * @param sessionId UUID of the active chat session
     * @return flow JSON string or {@code null}
     */
    public static String getFlow(UUID sessionId) {
        return SessionMemoryStore.getFlowJson(sessionId);
    }

    /**
     * Returns the stored Internal Flow Model for a session, or {@code null} if absent.
     *
     * @param sessionId UUID of the active chat session
     * @return FlowModel or {@code null}
     */
    public static FlowModel getFlowModel(UUID sessionId) {
        return SessionMemoryStore.getFlowModel(sessionId);
    }

    /**
     * Removes session memory for the given session ID.
     *
     * @param sessionId UUID of the session to clear
     */
    public static void clear(UUID sessionId) {
        SessionMemoryStore.clear(sessionId);
    }
}
