package com.nexusivr.ai.service;

import com.nexusivr.ai.ai.optimization.ConversationMemory;
import com.nexusivr.ai.ai.optimization.FlowSummaryBuilder;
import com.nexusivr.ai.model.MessageRole;
import com.nexusivr.ai.service.FlowContextService;

import com.nexusivr.ai.dao.AiSessionDao;
import com.nexusivr.ai.dao.MessageDao;
import com.nexusivr.ai.dto.ChatResponse;
import com.nexusivr.ai.dto.ConversationResponse;
import com.nexusivr.ai.exception.DataAccessException;
import com.nexusivr.ai.exception.ResourceNotFoundException;
import com.nexusivr.ai.exception.ServiceException;
import com.nexusivr.ai.exception.ValidationException;
import com.nexusivr.ai.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nexusivr.ai.controller.ServiceRegistry;
import com.nexusivr.ai.dto.response.FlowImprovementResponse;
import com.nexusivr.ai.dto.response.FlowValidationResponse;
import com.nexusivr.ai.dto.response.QuotaWarning;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Concrete service managing AI Chat sessions and message turn processing.
 *
 * <h3>Conversation Memory</h3>
 * <p>Every session has isolated memory managed by {@link SessionMemoryStore}:
 * <ul>
 *   <li>Last generated / optimized IVR flow (nodes + edges)</li>
 *   <li>Selected AI provider</li>
 *   <li>Generated summaries</li>
 * </ul>
 *
 * <h3>Deterministic Flow Queries</h3>
 * <p>When the user asks about the current flow (node counts, edge counts, queues, transfers, etc.),
 * the answer is computed directly from the stored {@link SessionMemoryStore.SessionMemory} —
 * the LLM is <em>never</em> invoked for these queries. This eliminates hallucination.
 *
 * <h3>LLM Context Grounding</h3>
 * <p>For all other questions, the serialized flow JSON is prepended to the LLM prompt so
 * the model answers in the context of the actual IVR topology.
 */
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private final AiSessionDao sessionDao;
    private final MessageDao messageDao;
    private final AiService aiService;
    private final FlowContextService flowContextService;
    private final ConversationMemory conversationMemory;

    public ChatService(AiSessionDao sessionDao, MessageDao messageDao, AiService aiService) {
        this(sessionDao, messageDao, aiService, new FlowContextService());
    }

    public ChatService(AiSessionDao sessionDao, MessageDao messageDao, AiService aiService, FlowContextService flowContextService) {
        this.sessionDao = Objects.requireNonNull(sessionDao, "sessionDao must not be null");
        this.messageDao = Objects.requireNonNull(messageDao, "messageDao must not be null");
        this.aiService  = Objects.requireNonNull(aiService,  "aiService must not be null");
        this.flowContextService = Objects.requireNonNull(flowContextService, "flowContextService must not be null");
        this.conversationMemory = new ConversationMemory();
    }

    public AiService getAiService() {
        return aiService;
    }

    // ----------------------------------------------------------------
    // Session management
    // ----------------------------------------------------------------

    /**
     * Creates and starts a new AI chat session for a tenant.
     */
    public AiSession startSession(UUID tenantId, Channel channel, String customerIdentifier) {
        if (tenantId == null) {
            throw new ValidationException("tenantId is required to start a session");
        }

        AiSession session = new AiSession();
        session.setId(UUID.randomUUID());
        session.setTenantId(tenantId);
        session.setChannel(channel != null ? channel : Channel.VOICE);
        session.setCustomerIdentifier(customerIdentifier);
        session.setStatus(SessionStatus.ACTIVE);

        try {
            return sessionDao.create(session);
        } catch (Exception e) {
            logger.warn("DB offline during startSession for tenant {}. Returning transient session.", tenantId);
            return session;
        }
    }

    // ----------------------------------------------------------------
    // Message sending
    // ----------------------------------------------------------------

    /**
     * Sends a user message turn and returns the AI assistant response.
     * Flow context is resolved automatically from {@link SessionMemoryStore}.
     */
    public ChatResponse sendMessage(UUID sessionId, UUID tenantId, String userMessage) {
        return sendMessage(sessionId, tenantId, userMessage, null);
    }

    /**
     * Sends a user message turn with an optional explicit IVR flow context JSON.
     *
     * <p>Priority for flow context:
     * <ol>
     *   <li>Explicit {@code flowContext} parameter (if non-blank)</li>
     *   <li>Flow JSON stored in {@link SessionMemoryStore} for this session</li>
     * </ol>
     */
    public ChatResponse sendMessage(UUID sessionId, UUID tenantId, String userMessage, String flowContext) {
        return sendMessage(sessionId, tenantId, userMessage, flowContext, null, null);
    }

    public ChatResponse sendMessage(UUID sessionId, UUID tenantId, String userMessage, String flowContext, UUID selectedSnapshotId) {
        return sendMessage(sessionId, tenantId, userMessage, flowContext, selectedSnapshotId, null);
    }

    public ChatResponse sendMessage(UUID sessionId, UUID tenantId, String userMessage, String flowContext, UUID selectedSnapshotId, Boolean autoRefine) {
        if (sessionId == null || tenantId == null) {
            throw new ValidationException("sessionId and tenantId are required");
        }
        if (userMessage == null || userMessage.trim().isEmpty()) {
            throw new ValidationException("userMessage cannot be empty");
        }

        // Intercept internal flow sync messages to avoid storing them in conversation history
        if (userMessage.startsWith("__flow_sync__:")) {
            if (isNonEmpty(flowContext)) {
                flowContextService.saveActiveFlow(sessionId, flowContext);
            }
            return new ChatResponse(sessionId, tenantId, "Flow synchronized.", MessageRole.ASSISTANT, 0, 0);
        }

        // ── Classify and route via AiOperationRouter ──────────────────
        AiOperation op = ServiceRegistry.getAiOperationRouter().classify(userMessage);

        // ── Persist flow in session memory if provided ──────────────────
        if (isNonEmpty(flowContext)) {
            String trimmed = flowContext.trim();
            if (trimmed.startsWith("<?xml") || trimmed.startsWith("<vxml")) {
                flowContextService.saveActiveFlow(sessionId, flowContext);
                logger.debug("[ChatService] VXML flow context provided in request — saved/updated via FlowContextService [{}]", sessionId);
            } else {
                logger.debug("[ChatService] JSON flow context provided in request — used for LLM grounding only, canonical model preserved [{}]", sessionId);
            }
        }

        if (op != AiOperation.CHAT) {
            Object routeResult = ServiceRegistry.getAiOperationRouter().route(op, sessionId, tenantId, userMessage, flowContext, selectedSnapshotId, null, null, null, null, autoRefine);
            ChatResponse chatResp;
            List<QuotaWarning> quotaWarnings = new ArrayList<>();
            if (routeResult instanceof ChatResponse) {
                chatResp = (ChatResponse) routeResult;
            } else if (routeResult instanceof com.nexusivr.ai.model.Flow) {
                com.nexusivr.ai.model.Flow flow = (com.nexusivr.ai.model.Flow) routeResult;
                StringBuilder replySb = new StringBuilder();
                if (flow.getRefinedPrompt() != null && !flow.getRefinedPrompt().isBlank()) {
                    replySb.append("✨ **Refined Specification:**\n")
                           .append(formatRefinedPrompt(flow.getRefinedPrompt()))
                           .append("\n\n");
                }
                if (flow.getDroppedFeatures() != null && !flow.getDroppedFeatures().isEmpty()) {
                    replySb.append("⚠️ **Note:** The following requested features could not be reliably connected into the flow structure and were removed: '")
                           .append(String.join("', '", flow.getDroppedFeatures()))
                           .append("'. You can ask me to add them back or regenerate.\n\n");
                }
                replySb.append("I have successfully generated a new IVR flow for you based on your description:\n\n```json\n")
                       .append(flow.getFlowJson())
                       .append("\n```");
                String reply = replySb.toString();
                chatResp = new ChatResponse(sessionId, tenantId, reply, MessageRole.ASSISTANT, 1, reply.length());
                chatResp.setFlowJson(flow.getFlowJson());
                chatResp.setRefinedPrompt(flow.getRefinedPrompt());
                chatResp.setDroppedFeatures(flow.getDroppedFeatures());
                if (flow.getQuotaWarnings() != null) quotaWarnings.addAll(flow.getQuotaWarnings());
                if (flow.isTemplateFallback()) {
                    chatResp.setTemplateFallback(true);
                    chatResp.setFallbackNotice(flow.getFallbackNotice());
                    FlowValidationResponse validationResponse = new FlowValidationResponse();
                    validationResponse.setTemplateFallback(true);
                    validationResponse.setFallbackNotice(flow.getFallbackNotice());
                    chatResp.setValidationResult(validationResponse);
                }
                if (flow.getSelectedProvider() != null || flow.getActualProviderUsed() != null) {
                    chatResp.setSelectedProvider(flow.getSelectedProvider());
                    chatResp.setActualProviderUsed(flow.getActualProviderUsed());
                    chatResp.setFallbackUsed(flow.getSelectedProvider() != null && flow.getActualProviderUsed() != null
                            && !flow.getSelectedProvider().equalsIgnoreCase(flow.getActualProviderUsed()));
                    if (chatResp.isFallbackUsed()) {
                        chatResp.setFallbackReason(flow.getSelectedProvider() + " failed. Response generated using " + flow.getActualProviderUsed() + ".");
                    }
                }
            } else if (routeResult instanceof FlowImprovementResponse) {
                FlowImprovementResponse improvement = (FlowImprovementResponse) routeResult;
                String changeSummary = improvement.getChangeLog().stream().collect(Collectors.joining("; "));
                String flowJsonToOutput = (improvement.getImprovedFlowJson() != null && !improvement.getImprovedFlowJson().isEmpty())
                        ? improvement.getImprovedFlowJson()
                        : new com.google.gson.Gson().toJson(improvement.getImprovedFlow());
                String replyHeader;
                if (improvement.isRolledBack()) {
                    replyHeader = "⚠️ The optimization attempt introduced more problems than it fixed, so no changes were applied — your flow remains unchanged.";
                } else if (improvement.isRegressed() || !improvement.isImproved()) {
                    replyHeader = "⚠️ The optimization attempt did not improve the flow. Changes were applied, but manual review is recommended.";
                } else {
                    replyHeader = "I have optimized the IVR flow.";
                }
                String reply = replyHeader + " Change log: " + changeSummary + "\n\n```json\n" + flowJsonToOutput + "\n```";
                chatResp = new ChatResponse(sessionId, tenantId, reply, MessageRole.ASSISTANT, 1, reply.length());
                chatResp.setFlowJson(flowJsonToOutput);
                if (improvement.getFinalValidation() != null) {
                    chatResp.setValidationResult(improvement.getFinalValidation());
                }
                if (improvement.getQuotaWarnings() != null) quotaWarnings.addAll(improvement.getQuotaWarnings());
                if (improvement.getSelectedProvider() != null || improvement.getActualProviderUsed() != null) {
                    chatResp.setSelectedProvider(improvement.getSelectedProvider());
                    chatResp.setActualProviderUsed(improvement.getActualProviderUsed());
                    chatResp.setFallbackUsed(improvement.isFallbackUsed());
                    chatResp.setFallbackReason(improvement.getFallbackReason());
                }
            } else if (routeResult instanceof FlowValidationResponse) {
                FlowValidationResponse validation = (FlowValidationResponse) routeResult;
                String reply = validation.isValid() ? "The current IVR flow is valid." : "The flow has validation issues:\n" + validation.getIssues().stream().map(i -> "- [" + i.getSeverity() + "] " + i.getMessage()).collect(Collectors.joining("\n"));
                chatResp = new ChatResponse(sessionId, tenantId, reply, MessageRole.ASSISTANT, 1, reply.length());
            } else {
                chatResp = new ChatResponse(sessionId, tenantId, "Operation routed but returned an unexpected type.", MessageRole.ASSISTANT, 1, 0);
            }
            if (!quotaWarnings.isEmpty()) {
                chatResp.setQuotaWarnings(quotaWarnings);
            }
            applyProviderMetadata(chatResp);
            saveMessagePair(sessionId, tenantId, userMessage, chatResp.getReplyMessage(), selectedSnapshotId);
            return chatResp;
        }

        try {
            // ── 1. Resolve or create session ─────────────────────────────
            AiSession session = sessionDao.findById(sessionId, tenantId).orElse(null);
            if (session == null) {
                session = new AiSession();
                session.setId(sessionId);
                session.setTenantId(tenantId);
                session.setChannel(Channel.CHAT);
                session.setCustomerIdentifier(truncateTitle(userMessage));
                session.setStatus(SessionStatus.ACTIVE);
                try {
                    sessionDao.create(session);
                } catch (Exception ex) {
                    logger.warn("Could not persist new AiSession in DB: {}", ex.getMessage());
                }
            } else if (isTitleUpdateNeeded(session)) {
                try {
                    sessionDao.updateTitle(sessionId, tenantId, truncateTitle(userMessage));
                } catch (Exception ex) {
                    logger.warn("Could not update session title in DB: {}", ex.getMessage());
                }
            }

            // ── 2. Persist user message ──────────────────────────────────
            Message userMsg = new Message();
            userMsg.setId(UUID.randomUUID());
            userMsg.setSessionId(sessionId);
            userMsg.setTenantId(tenantId);
            userMsg.setTurnNumber(0);
            userMsg.setRole(MessageRole.USER);
            userMsg.setContent(userMessage);
            userMsg.setMetadata(buildMetadata(userMsg.getId(), sessionId, selectedSnapshotId));
            messageDao.save(userMsg);

            // ── 3. Compute AI reply ──────────────────────────────────────
            String aiReply = computeReply(sessionId, tenantId, userMessage, flowContext, selectedSnapshotId);

            // ── 4. Persist AI reply ──────────────────────────────────────
            Message assistantMsg = new Message();
            assistantMsg.setId(UUID.randomUUID());
            assistantMsg.setSessionId(sessionId);
            assistantMsg.setTenantId(tenantId);
            assistantMsg.setTurnNumber(0);
            assistantMsg.setRole(MessageRole.ASSISTANT);
            assistantMsg.setContent(aiReply);
            assistantMsg.setModelUsed(com.nexusivr.ai.config.LlmConfig.getGroqModel());
            assistantMsg.setTokensInput(userMessage.length());
            assistantMsg.setTokensOutput(aiReply.length());
            assistantMsg.setMetadata(buildMetadata(assistantMsg.getId(), sessionId, selectedSnapshotId));
            messageDao.save(assistantMsg);

            List<QuotaWarning> quotaWarnings = new ArrayList<>();
            if (aiReply != null && aiReply.toLowerCase().contains("quota exceeded")) {
                String provider = "unknown";
                if (aiReply.toLowerCase().contains("gemini")) provider = "gemini";
                else if (aiReply.toLowerCase().contains("groq")) provider = "groq";
                quotaWarnings.add(new QuotaWarning(provider, null, 1));
            }

            ChatResponse response = new ChatResponse(sessionId, tenantId, aiReply, MessageRole.ASSISTANT,
                    assistantMsg.getTurnNumber(), userMessage.length() + aiReply.length());
            response.setQuotaWarnings(quotaWarnings);
            applyProviderMetadata(response);
            return response;

        } catch (ValidationException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("DB error during sendMessage session={} tenant={}. Falling back to stateless AI call.", sessionId, tenantId, e);
            String aiReply = computeReply(sessionId, tenantId, userMessage, flowContext, selectedSnapshotId);
            List<QuotaWarning> quotaWarnings = new ArrayList<>();
            if (aiReply != null && aiReply.toLowerCase().contains("quota exceeded")) {
                String provider = "unknown";
                if (aiReply.toLowerCase().contains("gemini")) provider = "gemini";
                else if (aiReply.toLowerCase().contains("groq")) provider = "groq";
                quotaWarnings.add(new QuotaWarning(provider, null, 1));
            }
            ChatResponse response = new ChatResponse(sessionId, tenantId, aiReply, MessageRole.ASSISTANT, 1,
                    userMessage.length() + aiReply.length());
            response.setQuotaWarnings(quotaWarnings);
            applyProviderMetadata(response);
            return response;
        }
    }

    private void applyProviderMetadata(ChatResponse response) {
        com.nexusivr.ai.ai.AiResponse lastResponse = com.nexusivr.ai.service.AiService.getLastProviderResponse();
        if (lastResponse != null) {
            response.setSelectedProvider(lastResponse.getSelectedProvider());
            response.setActualProviderUsed(lastResponse.getActualProviderUsed());
            boolean fallbackUsed = lastResponse.getSelectedProvider() != null
                    && lastResponse.getActualProviderUsed() != null
                    && !lastResponse.getSelectedProvider().equalsIgnoreCase(lastResponse.getActualProviderUsed());
            response.setFallbackUsed(fallbackUsed);
            if (fallbackUsed) {
                response.setFallbackReason(lastResponse.getSelectedProvider() + " failed. Response generated using " + lastResponse.getActualProviderUsed() + ".");
            }
        }
        com.nexusivr.ai.service.AiService.clearLastProviderResponse();
    }

    // ----------------------------------------------------------------
    // Core reply routing
    // ----------------------------------------------------------------

    /**
     * Routes the user message to either:
     * <ul>
     *   <li>A deterministic answer derived from stored session flow memory</li>
     *   <li>The LLM (with flow JSON as grounding context)</li>
     * </ul>
     */
    private String computeReply(UUID sessionId, UUID tenantId, String userMessage, String explicitFlowContext, UUID selectedSnapshotId) {
        String cleanMsg = userMessage.toLowerCase().trim();

        // Ensure conversation memory is populated from DB if starting from an existing session
        ensureSessionMemoryLoaded(sessionId, tenantId);

        // Record user message in conversation memory
        conversationMemory.addEntry(sessionId, "user", userMessage);

        // ── Attempt deterministic answer from stored flow ────────────────
        if (SessionMemoryStore.hasFlow(sessionId)) {
            String deterministic = handleFlowQuery(sessionId, cleanMsg);
            if (deterministic != null) {
                logger.debug("[ChatService] Deterministic flow answer for session {}: {}", sessionId, deterministic);
                conversationMemory.addEntry(sessionId, "assistant", deterministic);
                return deterministic;
            }
        } else {
            // No flow stored — guard against flow-related follow-ups
            if (isFlowFollowUp(cleanMsg)) {
                String guardMsg = "There is no generated IVR flow in the current session. " +
                        "Please generate a flow first using the AI Generate or Improve buttons.";
                conversationMemory.addEntry(sessionId, "assistant", guardMsg);
                return guardMsg;
            }
        }

        // ── Fall through to LLM with optional flow grounding ─────────────
        String resolvedFlow = null;
        if (isNonEmpty(explicitFlowContext)) {
            resolvedFlow = explicitFlowContext;
        } else if (selectedSnapshotId != null) {
            FlowSnapshot snap = ServiceRegistry.getFlowSnapshotService().getSnapshot(selectedSnapshotId);
            if (snap != null) {
                resolvedFlow = snap.getFlowJson();
            }
        }
        if (resolvedFlow == null) {
            resolvedFlow = flowContextService.getActiveFlow(sessionId);
        }

        // Use compact flow summary instead of full JSON for LLM context
        String flowContextForLlm = null;
        if (resolvedFlow != null && !resolvedFlow.isBlank() && !resolvedFlow.equals("{}")) {
            try {
                com.nexusivr.ai.model.flow.FlowModel flowModel = FlowContextService.convertJsonToModel(resolvedFlow);
                if (flowModel != null) {
                    flowContextForLlm = FlowSummaryBuilder.buildCompactSummary(flowModel);
                }
            } catch (Exception e) {
                logger.debug("[ChatService] Could not convert flow JSON to summary: {}", e.getMessage());
            }
        }

        // Use conversation memory with sliding window instead of full history
        List<ConversationMemory.MemoryEntry> recentEntries = conversationMemory.getEntriesForLlm(sessionId);
        List<Message> history = new ArrayList<>();
        int turnNumber = 1;
        for (ConversationMemory.MemoryEntry entry : recentEntries) {
            if (!"system".equals(entry.role())) {
                Message msg = new Message();
                msg.setRole(MessageRole.valueOf(entry.role().toUpperCase()));
                msg.setContent(entry.content());
                msg.setTurnNumber(turnNumber++);
                history.add(msg);
            }
        }

        String finalFlowContext = flowContextForLlm;
        String aiReply;
        try {
            aiReply = aiService.generateResponse(userMessage, history, finalFlowContext);
        } catch (com.nexusivr.ai.service.exception.ProviderException e) {
            logger.warn("[ChatService] All providers exhausted for chat query session={}: {}", sessionId, e.getMessage());
            aiReply = "I couldn't process that right now — please try again in a moment.";
        } catch (Exception e) {
            logger.warn("[ChatService] Unexpected error during chat response generation for session={}: {}", sessionId, e.getMessage());
            aiReply = "I couldn't process that right now — please try again in a moment.";
        }
        conversationMemory.addEntry(sessionId, "assistant", aiReply);
        return aiReply;
    }

    private void ensureSessionMemoryLoaded(UUID sessionId, UUID tenantId) {
        if (sessionId == null) return;
        if (!conversationMemory.getEntriesForLlm(sessionId).isEmpty()) return;
        try {
            List<Message> dbMessages = messageDao.findBySessionId(sessionId, tenantId);
            if (dbMessages != null && !dbMessages.isEmpty()) {
                logger.info("[ChatService] Preloading {} history message(s) from DB into conversationMemory for session {}", dbMessages.size(), sessionId);
                for (Message msg : dbMessages) {
                    if (msg.getRole() == MessageRole.USER) {
                        conversationMemory.addEntry(sessionId, "user", msg.getContent());
                    } else if (msg.getRole() == MessageRole.ASSISTANT) {
                        conversationMemory.addEntry(sessionId, "assistant", msg.getContent());
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("[ChatService] Preloading DB messages for session {} skipped: {}", sessionId, e.getMessage());
        }
    }

    // ----------------------------------------------------------------
    // Deterministic flow query handler
    // ----------------------------------------------------------------

    /**
     * Matches the user message against known flow-related patterns and returns
     * a precise answer computed directly from {@link SessionMemoryStore.SessionMemory}.
     *
     * @return answer string if matched, {@code null} if the message should go to the LLM
     */
    private String handleFlowQuery(UUID sessionId, String cleanMsg) {
        SessionMemoryStore.SessionMemory memory = SessionMemoryStore.get(sessionId);
        if (memory == null) return null;

        com.nexusivr.ai.model.flow.FlowModel flowModel = memory.getFlowModel();
        String flowId = flowModel != null && flowModel.getId() != null ? flowModel.getId() : "unknown-flow-id";
        logger.info("[ChatService Flow-Related Query] Answering flow question. sessionId: {}, flowId: {}, nodesCount: {}",
                sessionId, flowId, memory.getNodeCount());

        // ── Node count / What is this flow / Node names ──────────────────
        if (matches(cleanMsg, "how many nodes", "number of nodes", "node count", "nodes?")) {
            return "The IVR flow contains " + memory.getNodeCount() + " nodes.";
        }

        if (matches(cleanMsg, "tell me node names", "tell me their names", "node names", "list node names", "names of the nodes")) {
            return memory.buildNodeList();
        }

        if (matches(cleanMsg, "what nodes exist", "what nodes exist?", "what nodes are in the flow", "list nodes", "list the nodes", "show nodes", "all nodes")) {
            return memory.buildNodeList();
        }

        if (matches(cleanMsg, "what is this flow", "what is this flow?", "describe this flow", "explain this flow")) {
            return buildFlowSummary(memory);
        }

        // ── Edge count ───────────────────────────────────────────────────
        if (matches(cleanMsg, "how many edges", "number of edges", "edge count", "edges?")) {
            return "The IVR flow contains " + memory.getEdgeCount() + " connections (edges).";
        }

        // ── Queue nodes ──────────────────────────────────────────────────
        if (matches(cleanMsg, "queues exist", "what queues", "list queues", "list the queues",
                "how many queues", "number of queues", "queue count")) {
            if (cleanMsg.contains("how many") || cleanMsg.contains("number of") || cleanMsg.contains("count")) {
                return "The IVR flow contains " + memory.countNodesByType("queue") + " queue node(s).";
            }
            return memory.buildNodeListByType("queue", "Queue");
        }

        // ── Transfer nodes ───────────────────────────────────────────────
        if (matches(cleanMsg, "how many transfers", "number of transfers", "transfer count",
                "list transfers", "what transfers", "list the transfers")) {
            if (cleanMsg.contains("list") || cleanMsg.contains("what")) {
                return memory.buildNodeListByType("transfer", "Transfer");
            }
            return "The IVR flow contains " + memory.countNodesByType("transfer") + " transfer node(s).";
        }

        // ── Menu nodes ───────────────────────────────────────────────────
        if (matches(cleanMsg, "how many menus", "number of menus", "menu count",
                "list menus", "what menus")) {
            if (cleanMsg.contains("list") || cleanMsg.contains("what")) {
                return memory.buildNodeListByType("menu", "Menu");
            }
            return "The IVR flow contains " + memory.countNodesByType("menu") + " menu node(s).";
        }

        // ── Complexity / summary ─────────────────────────────────────────
        if (matches(cleanMsg, "complexity", "how complex", "flow summary", "flow details",
                "describe the flow", "what's in the flow", "what is in the flow")) {
            return buildFlowSummary(memory);
        }

        // ── Not a deterministic query — pass to LLM ───────────────────────
        return null;
    }

    /** Builds a concise summary of the current flow topology from session memory. */
    private String buildFlowSummary(SessionMemoryStore.SessionMemory memory) {
        int nodes     = memory.getNodeCount();
        int edges     = memory.getEdgeCount();
        int queues    = memory.countNodesByType("queue");
        int transfers = memory.countNodesByType("transfer");
        int menus     = memory.countNodesByType("menu");

        return String.format(
                "IVR Flow Summary:\n" +
                "- Total Nodes: %d\n" +
                "- Total Connections (Edges): %d\n" +
                "- Menu Nodes: %d\n" +
                "- Queue Nodes: %d\n" +
                "- Transfer Nodes: %d",
                nodes, edges, menus, queues, transfers);
    }

    // ----------------------------------------------------------------
    // Session management helpers
    // ----------------------------------------------------------------

    public List<AiSession> getAllSessions(UUID tenantId) {
        if (tenantId == null) throw new ValidationException("tenantId is required");
        try {
            return sessionDao.findAllSessions(tenantId);
        } catch (Exception e) {
            logger.warn("DB error fetching sessions for tenant {}", tenantId, e);
            return List.of();
        }
    }

    public ConversationResponse getConversationHistory(UUID sessionId, UUID tenantId) {
        if (sessionId == null || tenantId == null)
            throw new ValidationException("sessionId and tenantId are required");
        try {
            List<Message> messages = messageDao.findBySessionId(sessionId, tenantId);
            return new ConversationResponse(sessionId, tenantId, messages, messages.size());
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("DB offline fetching history for session {}", sessionId);
            return new ConversationResponse(sessionId, tenantId, List.of(), 0);
        }
    }

    public boolean deleteSession(UUID sessionId, UUID tenantId) {
        if (sessionId == null || tenantId == null)
            throw new ValidationException("sessionId and tenantId are required");
        try {
            messageDao.deleteBySessionId(sessionId, tenantId);
            SessionMemoryStore.clear(sessionId);
            return sessionDao.delete(sessionId, tenantId);
        } catch (Exception e) {
            logger.error("Error deleting session {} tenant {}", sessionId, tenantId, e);
            throw new ServiceException("Error deleting AI session", e);
        }
    }

    public boolean updateSessionTitle(UUID sessionId, UUID tenantId, String title) {
        if (sessionId == null || tenantId == null)
            throw new ValidationException("sessionId and tenantId are required");
        if (title == null || title.isBlank())
            throw new ValidationException("title cannot be empty");
        try {
            return sessionDao.updateTitle(sessionId, tenantId, title.trim());
        } catch (Exception e) {
            logger.error("Error updating session title for session {} tenant {}", sessionId, tenantId, e);
            return false;
        }
    }

    public boolean endSession(UUID sessionId, UUID tenantId) {
        if (sessionId == null || tenantId == null)
            throw new ValidationException("sessionId and tenantId are required");
        try {
            SessionMemoryStore.clear(sessionId);
            return sessionDao.endSession(sessionId, tenantId);
        } catch (DataAccessException e) {
            logger.error("Error ending session {} tenant {}", sessionId, tenantId, e);
            throw new ServiceException("Error ending AI session", e);
        }
    }

    // ----------------------------------------------------------------
    // Static helpers
    // ----------------------------------------------------------------

    /** Returns true if {@code value} is non-null, non-blank, and not a bare empty JSON object. */
    private static boolean isNonEmpty(String value) {
        return value != null && !value.isBlank() && !value.equals("{}");
    }

    /** Returns true if any of the given keywords are contained in {@code cleanMsg}. */
    private static boolean matches(String cleanMsg, String... keywords) {
        for (String kw : keywords) {
            if (cleanMsg.contains(kw)) return true;
        }
        return false;
    }

    /**
     * Returns true if the message is clearly a follow-up question about a flow
     * (node, edge, queue, transfer, etc.) but not a generation request.
     */
    private static boolean isFlowFollowUp(String cleanMsg) {
        boolean hasFlowKeyword = matches(cleanMsg, "node", "edge", "queue", "transfer", "menu",
                "ivr", "flow", "complexity");
        boolean isGeneration   = matches(cleanMsg, "generate", "create", "build", "make", "design");
        return hasFlowKeyword && !isGeneration;
    }

    private static String truncateTitle(String msg) {
        return msg.length() > 30 ? msg.substring(0, 30) + "..." : msg;
    }

    private static boolean isTitleUpdateNeeded(AiSession session) {
        String id = session.getCustomerIdentifier();
        return id == null || id.isBlank() || id.startsWith("Flow Session");
    }

    /**
     * Classifies the intent of a user message (Requirement 1, 2, 3, 4).
     */
    private String detectIntent(String message, boolean hasActiveFlow) {
        if (message == null) return "GENERAL_CHAT";
        String msg = message.toLowerCase().trim();

        // 1. EXPORT_FLOW
        if (msg.contains("export this flow") || msg.contains("download json") || 
            msg.contains("give me the json code") || msg.contains("export flow") || 
            msg.contains("get json of this design")) {
            return "EXPORT_FLOW";
        }

        // 2. FLOW_ANALYSIS
        if (msg.contains("validate this flow") || msg.contains("review this flow") || 
            msg.contains("analyze this flow") || msg.contains("is this valid") || 
            msg.contains("compare this flow") || msg.contains("summarize this flow") ||
            msg.contains("validate this json") || msg.contains("review this ivr")) {
            return "FLOW_ANALYSIS";
        }

        // 3. FLOW_QUESTION
        if (msg.contains("how many nodes") || msg.contains("number of nodes") || 
            msg.contains("node count") || msg.contains("nodes?") ||
            msg.contains("tell me node names") || msg.contains("tell me their names") || 
            msg.contains("node names") || msg.contains("list node names") || 
            msg.contains("names of the nodes") || msg.contains("what nodes exist") || 
            msg.contains("what nodes are in the flow") || msg.contains("list nodes") || 
            msg.contains("show nodes") || msg.contains("all nodes") ||
            msg.contains("what is this flow") || msg.contains("describe this flow") || 
            msg.contains("explain this flow") || msg.contains("how complex") ||
            msg.contains("is this the exact json") || msg.contains("is this simplified") ||
            msg.contains("explain this design") || msg.contains("what does node") || 
            msg.contains("why is this edge") || msg.contains("is this react flow json") || 
            msg.contains("is this the same as the canvas")) {
            return "FLOW_QUESTION";
        }

        // 4. GENERATE_FLOW
        if (msg.startsWith("generate") || msg.startsWith("create") || 
            msg.startsWith("design") || msg.startsWith("build") || 
            msg.startsWith("make") || msg.startsWith("produce") ||
            msg.contains("generate json from scratch")) {
            return "GENERATE_FLOW";
        }

        // If active flow exists and user asks about flow/nodes/edges, default to FLOW_QUESTION
        if (hasActiveFlow && (msg.contains("flow") || msg.contains("node") || msg.contains("edge") || msg.contains("json"))) {
            return "FLOW_QUESTION";
        }

        return "GENERAL_CHAT";
    }

    private void saveMessagePair(UUID sessionId, UUID tenantId, String userMessage, String aiReply, UUID selectedSnapshotId) {
        try {
            com.nexusivr.ai.model.AiSession session = sessionDao.findById(sessionId, tenantId).orElse(null);
            if (session == null) {
                session = new com.nexusivr.ai.model.AiSession();
                session.setId(sessionId);
                session.setTenantId(tenantId);
                session.setChannel(com.nexusivr.ai.model.Channel.CHAT);
                session.setCustomerIdentifier(truncateTitle(userMessage));
                session.setStatus(com.nexusivr.ai.model.SessionStatus.ACTIVE);
                sessionDao.create(session);
            }

            Message userMsg = new Message();
            userMsg.setId(UUID.randomUUID());
            userMsg.setSessionId(sessionId);
            userMsg.setTenantId(tenantId);
            userMsg.setTurnNumber(0);
            userMsg.setRole(MessageRole.USER);
            userMsg.setContent(userMessage);
            userMsg.setMetadata(buildMetadata(userMsg.getId(), sessionId, selectedSnapshotId));
            messageDao.save(userMsg);

            Message assistantMsg = new Message();
            assistantMsg.setId(UUID.randomUUID());
            assistantMsg.setSessionId(sessionId);
            assistantMsg.setTenantId(tenantId);
            assistantMsg.setTurnNumber(0);
            assistantMsg.setRole(MessageRole.ASSISTANT);
            assistantMsg.setContent(aiReply);
            assistantMsg.setModelUsed(com.nexusivr.ai.config.LlmConfig.getGroqModel());
            assistantMsg.setTokensInput(userMessage.length());
            assistantMsg.setTokensOutput(aiReply.length());
            assistantMsg.setMetadata(buildMetadata(assistantMsg.getId(), sessionId, selectedSnapshotId));
            messageDao.save(assistantMsg);
        } catch (Exception e) {
            logger.warn("Could not save message pair: {}", e.getMessage());
        }
    }

    private String buildMetadata(UUID messageId, UUID sessionId, UUID selectedSnapshotId) {
        try {
            FlowSnapshot snap = null;
            if (selectedSnapshotId != null) {
                snap = ServiceRegistry.getFlowSnapshotService().getSnapshot(selectedSnapshotId);
            }
            if (snap == null) {
                snap = ServiceRegistry.getFlowSnapshotService().getLatestSnapshot(sessionId);
            }

            com.google.gson.JsonObject metadataJson = new com.google.gson.JsonObject();
            metadataJson.addProperty("messageId", messageId.toString());
            metadataJson.addProperty("conversationId", sessionId.toString());

            if (snap != null) {
                metadataJson.addProperty("flowId", snap.getFlowId().toString());
                metadataJson.addProperty("snapshotId", snap.getSnapshotId().toString());
                metadataJson.addProperty("version", snap.getVersion());

                com.nexusivr.ai.model.flow.FlowModel flowModel = SessionMemoryStore.getFlowModel(sessionId);
                if (flowModel != null) {
                    metadataJson.addProperty("flowName", flowModel.getName() != null ? flowModel.getName() : "");
                    com.google.gson.JsonArray nodesArray = new com.google.gson.JsonArray();
                    for (com.nexusivr.ai.model.flow.FlowNode node : flowModel.getNodes()) {
                        com.google.gson.JsonObject nodeObj = new com.google.gson.JsonObject();
                        nodeObj.addProperty("id", node.getId());
                        nodeObj.addProperty("type", node.getType() != null ? node.getType().getBuilderType() : "UNKNOWN");
                        nodeObj.addProperty("label", node.getTitle() != null ? node.getTitle() : node.getId());
                        nodesArray.add(nodeObj);
                    }
                    metadataJson.add("nodes", nodesArray);
                    com.google.gson.JsonArray edgesArray = new com.google.gson.JsonArray();
                    for (com.nexusivr.ai.model.flow.FlowConnection conn : flowModel.getConnections()) {
                        com.google.gson.JsonObject edgeObj = new com.google.gson.JsonObject();
                        edgeObj.addProperty("id", conn.getId());
                        edgeObj.addProperty("sourceId", conn.getSourceNodeId());
                        edgeObj.addProperty("sourcePort", conn.getSourcePort());
                        edgeObj.addProperty("targetId", conn.getTargetNodeId());
                        edgeObj.addProperty("targetPort", conn.getTargetPort());
                        edgesArray.add(edgeObj);
                    }
                    metadataJson.add("edges", edgesArray);
                }
            }
            return metadataJson.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    public static String formatRefinedPrompt(String refinedSpec) {
        if (refinedSpec == null || refinedSpec.isBlank()) {
            return "";
        }
        try {
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(refinedSpec).getAsJsonObject();
            StringBuilder sb = new StringBuilder();
            if (json.has("business_domain") && !json.get("business_domain").isJsonNull()) {
                String d = json.get("business_domain").getAsString();
                sb.append("**Domain:** ").append(d.substring(0, 1).toUpperCase()).append(d.substring(1)).append("\n");
            }
            if (json.has("departments") && json.get("departments").isJsonArray()) {
                sb.append("**Departments:** ");
                List<String> depts = new ArrayList<>();
                json.getAsJsonArray("departments").forEach(e -> depts.add(e.getAsString()));
                sb.append(String.join(", ", depts)).append("\n");
            }
            if (json.has("menu_options") && json.get("menu_options").isJsonArray()) {
                sb.append("**Menu Options:**\n");
                json.getAsJsonArray("menu_options").forEach(e -> sb.append("- ").append(e.getAsString()).append("\n"));
            }
            if (json.has("refined_prompt") && !json.get("refined_prompt").isJsonNull()) {
                sb.append("**Specification:** ").append(json.get("refined_prompt").getAsString()).append("\n");
            }
            String formatted = sb.toString().trim();
            return !formatted.isEmpty() ? formatted : refinedSpec;
        } catch (Exception e) {
            return refinedSpec;
        }
    }
}
