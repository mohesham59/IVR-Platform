package com.nexusivr.ai.service;

import com.nexusivr.ai.model.flow.FlowModel;
import com.nexusivr.ai.model.flow.FlowNode;
import com.nexusivr.ai.model.flow.FlowConnection;
import com.nexusivr.ai.model.flow.FlowNodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-session isolated conversation memory store for NexusIVR AI sessions.
 *
 * <p>Each session stores:
 * <ul>
 *   <li>The Internal Flow Model ({@link FlowModel}) — the single source of truth</li>
 *   <li>Pre-computed node/edge counts for fast follow-up queries</li>
 *   <li>The active AI provider name (groq / ollama)</li>
 *   <li>Generated summaries</li>
 * </ul>
 *
 * <p>This store NEVER parses raw LLM output, JSON, or VoiceXML.
 * It only accepts and stores {@link FlowModel} objects.
 * React Flow JSON is rendered on demand via {@link #getFlowJson(UUID)}.
 *
 * <p>Memory is completely isolated per session UUID. No global static shared flow state.
 * Thread-safe via {@link ConcurrentHashMap}.
 */
public class SessionMemoryStore {

    private static final Logger logger = LoggerFactory.getLogger(SessionMemoryStore.class);

    private static volatile com.nexusivr.ai.service.ModelToFlowRenderer modelToFlowRenderer;

    // ----------------------------------------------------------------
    // Singleton map — one SessionMemory per UUID
    // ----------------------------------------------------------------
    private static final Map<UUID, SessionMemory> store = new ConcurrentHashMap<>();

    private SessionMemoryStore() {}

    /**
     * Sets the {@link ModelToFlowRenderer} used to render stored FlowModels
     * to React Flow JSON on demand. Must be set before calling {@link #getFlowJson(UUID)}.
     */
    public static void setModelToFlowRenderer(com.nexusivr.ai.service.ModelToFlowRenderer renderer) {
        modelToFlowRenderer = renderer;
    }

    // ----------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------

    /**
     * Saves (or replaces) the Internal Flow Model for a session.
     * <p>
     * This is the ONLY way to store flow data. No string parsing is performed.
     *
     * @param sessionId the active chat session UUID
     * @param flowModel the Internal Flow Model to store
     */
    public static void saveModel(UUID sessionId, FlowModel flowModel) {
        if (sessionId == null || flowModel == null) {
            return;
        }
        SessionMemory memory = store.computeIfAbsent(sessionId, id -> new SessionMemory(id));
        memory.setFlowModel(flowModel);
        logger.debug("SessionMemoryStore: Saved FlowModel for session {}. Nodes={}, Connections={}",
                sessionId, flowModel.getNodes().size(), flowModel.getConnections().size());
    }

    /**
     * Sets the active provider name for a session.
     *
     * @param sessionId    the active chat session UUID
     * @param providerName groq | ollama
     */
    public static void setProvider(UUID sessionId, String providerName) {
        if (sessionId == null || providerName == null) return;
        store.computeIfAbsent(sessionId, id -> new SessionMemory(id))
             .setProvider(providerName);
    }

    /**
     * Sets the business domain for a session.
     */
    public static void setDomain(UUID sessionId, String domain) {
        if (sessionId == null || domain == null || domain.isBlank()) return;
        store.computeIfAbsent(sessionId, id -> new SessionMemory(id))
             .setDomain(domain);
    }

    /**
     * Returns the business domain for a session, or null if unassigned/generic.
     */
    public static String getDomain(UUID sessionId) {
        SessionMemory m = get(sessionId);
        return m != null ? m.getDomain() : null;
    }

    /**
     * Appends a generated summary to a session's memory.
     *
     * @param sessionId the active chat session UUID
     * @param summary   the generated summary text
     */
    public static void addSummary(UUID sessionId, String summary) {
        if (sessionId == null || summary == null || summary.isBlank()) return;
        store.computeIfAbsent(sessionId, id -> new SessionMemory(id))
             .addSummary(summary);
    }

    /**
     * Returns the session memory object for a given session, or {@code null} if absent.
     */
    public static SessionMemory get(UUID sessionId) {
        return sessionId != null ? store.get(sessionId) : null;
    }

    /**
     * Returns the stored flow rendered as React Flow JSON for a session,
     * or {@code null} if no flow has been generated.
     * <p>
     * The JSON is rendered on demand from the Internal Flow Model.
     */
    public static String getFlowJson(UUID sessionId) {
        SessionMemory m = get(sessionId);
        if (m == null || m.getFlowModel() == null) {
            return null;
        }
        return m.renderFlowJson();
    }

    /**
     * Returns the stored Internal Flow Model for a session,
     * or {@code null} if no flow has been generated.
     */
    public static FlowModel getFlowModel(UUID sessionId) {
        SessionMemory m = get(sessionId);
        return m != null ? m.getFlowModel() : null;
    }

    /**
     * Returns true if a non-empty flow has been stored for this session.
     */
    public static boolean hasFlow(UUID sessionId) {
        SessionMemory m = get(sessionId);
        return m != null && m.getFlowModel() != null && !m.getFlowModel().getNodes().isEmpty();
    }

    /**
     * Removes all memory for a session (call on session end / delete).
     */
    public static void clear(UUID sessionId) {
        if (sessionId != null) {
            store.remove(sessionId);
            logger.debug("SessionMemoryStore: Cleared memory for session {}", sessionId);
        }
    }

    // ----------------------------------------------------------------
    // Inner class: SessionMemory
    // ----------------------------------------------------------------

    /**
     * Value object holding all memory for a single AI chat session.
     * All fields are mutable so they can be updated as the session evolves.
     */
    public static class SessionMemory {

        private final UUID sessionId;
        private final Instant createdAt;

        /** The Internal Flow Model — single source of truth. */
        private volatile FlowModel flowModel;

        /** Active LLM provider for this session. */
        private volatile String provider;

        /** Business domain for this session. */
        private volatile String domain;

        /** Accumulated AI-generated summaries for this session. */
        private final List<String> summaries = new java.util.concurrent.CopyOnWriteArrayList<>();

        SessionMemory(UUID sessionId) {
            this.sessionId = sessionId;
            this.createdAt = Instant.now();
        }

        // ---- Flow Model access ----

        void setFlowModel(FlowModel flowModel) {
            this.flowModel = flowModel;
        }

        FlowModel getFlowModel() {
            return flowModel;
        }

        /**
         * Renders the stored FlowModel to React Flow JSON on demand.
         * Returns null if no model is stored or no renderer is configured.
         */
        String renderFlowJson() {
            FlowModel model = this.flowModel;
            if (model == null) {
                return null;
            }
            com.nexusivr.ai.service.ModelToFlowRenderer renderer = modelToFlowRenderer;
            if (renderer == null) {
                logger.warn("SessionMemory[{}]: ModelToFlowRenderer not configured. Cannot render FlowModel to JSON.", sessionId);
                return null;
            }
            try {
                return renderer.render(model);
            } catch (Exception e) {
                logger.warn("SessionMemory[{}]: Failed to render FlowModel to JSON: {}", sessionId, e.getMessage());
                return null;
            }
        }

        // ---- Node / Edge queries (operate directly on FlowModel) ----

        public int getNodeCount() {
            FlowModel model = this.flowModel;
            return model != null ? model.getNodes().size() : 0;
        }

        public int getEdgeCount() {
            FlowModel model = this.flowModel;
            return model != null ? model.getConnections().size() : 0;
        }

        /**
         * Counts nodes whose {@link FlowNodeType} matches {@code nodeType} (case-insensitive).
         */
        public int countNodesByType(String nodeType) {
            if (nodeType == null) return 0;
            FlowModel model = this.flowModel;
            if (model == null || model.getNodes().isEmpty()) return 0;
            int count = 0;
            for (FlowNode node : model.getNodes()) {
                if (node.getType() != null && nodeType.equalsIgnoreCase(node.getType().name())) {
                    count++;
                }
            }
            return count;
        }

        /**
         * Returns a human-readable bullet list of node labels and types.
         * Example: {@code - Main Menu (MENU)}
         */
        public String buildNodeList() {
            FlowModel model = this.flowModel;
            if (model == null || model.getNodes().isEmpty()) {
                return "No nodes found in the flow.";
            }
            StringBuilder sb = new StringBuilder("Here are the nodes in the IVR flow:\n");
            for (FlowNode node : model.getNodes()) {
                sb.append(String.format("- %s (%s)%n", resolveLabel(node), node.getType()));
            }
            return sb.toString().trim();
        }

        /**
         * Returns a bullet list of node labels whose {@link FlowNodeType} equals {@code nodeType}.
         */
        public String buildNodeListByType(String nodeType, String friendlyName) {
            FlowModel model = this.flowModel;
            if (model == null || model.getNodes().isEmpty()) {
                return "There are no " + friendlyName + " nodes in the IVR flow.";
            }
            List<String> matches = new java.util.ArrayList<>();
            for (FlowNode node : model.getNodes()) {
                if (node.getType() != null && nodeType.equalsIgnoreCase(node.getType().name())) {
                    matches.add(resolveLabel(node));
                }
            }
            if (matches.isEmpty()) {
                return "There are no " + friendlyName + " nodes in the IVR flow.";
            }
            StringBuilder sb = new StringBuilder("The following " + friendlyName + " nodes exist:\n");
            for (String name : matches) {
                sb.append("- ").append(name).append("\n");
            }
            return sb.toString().trim();
        }

        private String resolveLabel(FlowNode node) {
            String label = node.getTitle();
            if (label == null || label.isBlank()) {
                label = node.getSubtitle();
            }
            if (label == null || label.isBlank()) {
                label = node.getId();
            }
            return label != null ? label : "Unnamed Node";
        }

        // ---- Getters ----

        public UUID getSessionId()       { return sessionId; }
        public Instant getCreatedAt()    { return createdAt; }
        public String getProvider()      { return provider; }
        public String getDomain()        { return domain; }
        public List<String> getSummaries() { return summaries; }

        // ---- Setters ----

        void setProvider(String provider)   { this.provider = provider; }
        void setDomain(String domain) {
            if (domain != null && !domain.isBlank() && !"generic".equalsIgnoreCase(domain)) {
                this.domain = domain;
            }
        }
        void addSummary(String summary)     { this.summaries.add(summary); }
    }
}
