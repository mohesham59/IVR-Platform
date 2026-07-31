package com.nexusivr.ai.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nexusivr.ai.dto.ChatResponse;
import com.nexusivr.ai.dto.common.FlowDto;
import com.nexusivr.ai.dto.response.FlowImprovementResponse;
import com.nexusivr.ai.dto.response.FlowValidationResponse;
import com.nexusivr.ai.model.Flow;
import com.nexusivr.ai.model.FlowSnapshot;
import com.nexusivr.ai.model.Message;
import com.nexusivr.ai.model.MessageRole;
import com.nexusivr.ai.model.flow.FlowModel;
import com.nexusivr.ai.ai.agents.SpecializedAgentService;
import com.nexusivr.ai.ai.optimization.FlowSummaryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AI Operation Router responsible for classifying requests and routing them to either the LLM or
 * direct FlowContextService/FlowSnapshotService retrievals.
 */
public class AiOperationRouter {

    private static final Logger logger = LoggerFactory.getLogger(AiOperationRouter.class);

    private final UnifiedAiEngine unifiedAiEngine;
    private final ChatService chatService;
    private final SpecializedAgentService specializedAgentService;

    public AiOperationRouter(UnifiedAiEngine unifiedAiEngine, ChatService chatService) {
        this(unifiedAiEngine, chatService, new SpecializedAgentService(com.nexusivr.ai.controller.ServiceRegistry.getProviderManager()));
    }

    public AiOperationRouter(UnifiedAiEngine unifiedAiEngine, ChatService chatService, SpecializedAgentService specializedAgentService) {
        this.unifiedAiEngine = unifiedAiEngine;
        this.chatService = chatService;
        this.specializedAgentService = specializedAgentService;
    }

    /**
     * Classifies user prompts into one of the supported AI operations.
     */
    public AiOperation classify(String message) {
        if (message == null || message.isBlank()) {
            return AiOperation.CHAT;
        }
        String clean = message.toLowerCase().trim();

        // 1. Export JSON / Canvas checks
        if (clean.contains("export this flow") || clean.contains("download json") || 
            clean.contains("give me the json code") || clean.contains("get json of this design") ||
            clean.contains("is this the exact json") || clean.contains("is this simplified") ||
            clean.contains("is this react flow json") || clean.contains("is this the same as the canvas") ||
            clean.contains("show me the json") || clean.contains("give me the json")) {
            return AiOperation.EXPORT_JSON;
        }

        // 2. Export XML
        if (clean.contains("export xml") || clean.contains("xml code") || clean.contains("download xml") || clean.contains("give me the xml")) {
            return AiOperation.EXPORT_XML;
        }

        // 3. Export Flow
        if (clean.contains("export flow") || clean.contains("download flow")) {
            return AiOperation.EXPORT_FLOW;
        }

        // 4. Node counts
        if (clean.contains("node") && (clean.contains("count") || clean.contains("how many") || clean.contains("number of") || clean.contains("nodes?"))) {
            return AiOperation.NODE_COUNT;
        }

        // 5. Node names
        if (clean.contains("their names") || clean.contains("node names") ||
            (clean.contains("node") && (clean.contains("name") || clean.contains("list") || clean.contains("show") || clean.contains("names of")))) {
            return AiOperation.NODE_NAMES;
        }

        // 6. Flow validation
        if (clean.contains("validate") || clean.contains("is this valid")) {
            return AiOperation.VALIDATE_FLOW;
        }

        // 7. Flow summary
        if (clean.contains("summarize") || clean.contains("summary") || clean.contains("explain this flow") || clean.contains("describe this flow")) {
            return AiOperation.FLOW_SUMMARY;
        }

        // 8. Flow generation
        if (clean.startsWith("generate") || clean.startsWith("create") || clean.startsWith("build") || clean.startsWith("make") || clean.contains("generate json from scratch")) {
            return AiOperation.GENERATE_FLOW;
        }

        // 9. Flow improvement & suggestion application
        if (clean.contains("improve") || clean.contains("optimize") || clean.contains("apply suggestion") || clean.startsWith("apply ai suggestion") || clean.startsWith("apply suggestion")) {
            return AiOperation.IMPROVE_FLOW;
        }

        // 11. Specialized agent invocation
        if (clean.startsWith("use agent:") || clean.startsWith("invoke agent:") || clean.startsWith("agent:")) {
            String agentId = clean.replaceFirst("(use agent:|invoke agent:|agent:)", "").trim().split("\\s+")[0];
            if (specializedAgentService.listAgents().containsKey(agentId)) {
                return AiOperation.INVOKE_AGENT;
            }
        }

        // 12. Domain-based new flow detection — bare business/domain names like
        //     "Telecom Customer Support" or "Pizza Restaurant" should generate
        //     a fresh flow, not be treated as chat about the existing one.
        if (!isFlowReference(clean) && !isQuestionFormat(clean)) {
            String domain = DomainDetector.detect(message);
            if (domain != null && !"generic".equals(domain) && !"technical_support".equals(domain)) {
                logger.info("[AiOperationRouter] Domain '{}' detected in prompt — classifying as GENERATE_FLOW: {}", domain, message);
                return AiOperation.GENERATE_FLOW;
            }
        }

        return AiOperation.CHAT;
    }

    /**
     * Routes and dispatches the execution based on operation type.
     */
    public Object route(AiOperation op, UUID sessionId, UUID tenantId, String userMessage, String flowContext) {
        return route(op, sessionId, tenantId, userMessage, flowContext, null, null, null, -1.0, -1, null);
    }

    public Object route(AiOperation op, UUID sessionId, UUID tenantId, String userMessage, String flowContext, UUID selectedSnapshotId,
                        String provider, String model, Double temp, Integer timeout) {
        return route(op, sessionId, tenantId, userMessage, flowContext, selectedSnapshotId, provider, model, temp, timeout, null);
    }

    public Object route(AiOperation op, UUID sessionId, UUID tenantId, String userMessage, String flowContext, UUID selectedSnapshotId,
                        String provider, String model, Double temp, Integer timeout, Boolean autoRefine) {
        logger.info("[AiOperationRouter] Dispatching operation={} for session={}, snapshotId={}, provider={}, model={}, autoRefine={}",
                op, sessionId, selectedSnapshotId, provider, model, autoRefine);

        switch (op) {
            case EXPORT_JSON: {
                String activeFlow = null;
                if (selectedSnapshotId != null) {
                    FlowSnapshot snap = com.nexusivr.ai.controller.ServiceRegistry.getFlowSnapshotService().getSnapshot(selectedSnapshotId);
                    if (snap != null) {
                        activeFlow = unifiedAiEngine.resolveStoredFlowToJson(snap.getFlowJson());
                    }
                }
                if (activeFlow == null) {
                    activeFlow = unifiedAiEngine.resolveStoredFlowToJson(unifiedAiEngine.getFlowContextService().getActiveFlow(sessionId));
                }
                if (activeFlow == null || activeFlow.isBlank() || activeFlow.equals("{}")) {
                    return new ChatResponse(sessionId, tenantId, "There is no generated IVR flow in the current session.", MessageRole.ASSISTANT, 1, 0);
                }
                return new ChatResponse(sessionId, tenantId, "Here is the exact React Flow JSON currently used by the IVR Builder canvas:\n```json\n" + activeFlow + "\n```", MessageRole.ASSISTANT, 1, activeFlow.length());
            }

            case EXPORT_FLOW: {
                String activeFlow = null;
                if (selectedSnapshotId != null) {
                    FlowSnapshot snap = com.nexusivr.ai.controller.ServiceRegistry.getFlowSnapshotService().getSnapshot(selectedSnapshotId);
                    if (snap != null) {
                        activeFlow = unifiedAiEngine.resolveStoredFlowToJson(snap.getFlowJson());
                    }
                }
                if (activeFlow == null) {
                    activeFlow = unifiedAiEngine.resolveStoredFlowToJson(unifiedAiEngine.getFlowContextService().getActiveFlow(sessionId));
                }
                if (activeFlow == null || activeFlow.isBlank() || activeFlow.equals("{}")) {
                    return new ChatResponse(sessionId, tenantId, "There is no generated IVR flow in the current session.", MessageRole.ASSISTANT, 1, 0);
                }
                // Use compact summary instead of full JSON for LLM context
                try {
                    com.nexusivr.ai.model.flow.FlowModel flowModel = com.nexusivr.ai.service.FlowContextService.convertJsonToModel(activeFlow);
                    if (flowModel != null) {
                        String compactSummary = FlowSummaryBuilder.buildCompactSummary(flowModel);
                        String prompt = "Review the following IVR flow summary and write a descriptive summary of what it does, the call routing logic, and queue setup. Do not generate or modify any flow JSON:\n" + compactSummary;
                        String summary = chatService.getAiService().generateResponse(prompt, List.of());
                        return new ChatResponse(sessionId, tenantId, summary, MessageRole.ASSISTANT, 1, summary.length());
                    }
                } catch (Exception e) {
                    logger.warn("[AiOperationRouter] Failed to convert flow JSON to summary: {}", e.getMessage());
                }
                return new ChatResponse(sessionId, tenantId, "Here is your exported flow data:\n\n" + activeFlow, MessageRole.ASSISTANT, 1, activeFlow.length());
            }

            case EXPORT_XML: {
                // Fix #8: Auto-detect snapshot format (VoiceXML vs JSON) instead of
                // blindly calling convertJsonToModel() which fails with MalformedJsonException
                // when the snapshot stores VoiceXML (the normal case).
                String xml = null;
                if (selectedSnapshotId != null) {
                    FlowSnapshot snap = com.nexusivr.ai.controller.ServiceRegistry.getFlowSnapshotService().getSnapshot(selectedSnapshotId);
                    if (snap != null) {
                        xml = resolveSnapshotToVxml(snap.getFlowJson());
                    }
                }
                if (xml == null) {
                    FlowSnapshot latestSnap = com.nexusivr.ai.controller.ServiceRegistry.getFlowSnapshotService().getLatestSnapshot(sessionId);
                    if (latestSnap != null) {
                        xml = resolveSnapshotToVxml(latestSnap.getFlowJson());
                    }
                }
                if (xml == null) {
                    com.nexusivr.ai.model.flow.FlowModel flowModel = unifiedAiEngine.getFlowContextService().getActiveFlowModel(sessionId);
                    if (flowModel != null) {
                        try {
                            xml = new com.nexusivr.ai.service.ModelToVxmlExporter().export(flowModel);
                        } catch (Exception e) {
                            logger.warn("[AiOperationRouter] Failed to export FlowModel to VoiceXML: {}", e.getMessage());
                        }
                    }
                }
                if (xml == null || xml.isBlank()) {
                    return new ChatResponse(sessionId, tenantId, "There is no generated IVR flow in the current session.", MessageRole.ASSISTANT, 1, 0);
                }
                String sanitizedXml = UnifiedAiEngine.stripMarkdownCodeFences(xml.trim());
                return new ChatResponse(sessionId, tenantId, "Here is the flow design exported in XML format:\n```xml\n" + sanitizedXml + "\n```", MessageRole.ASSISTANT, 1, sanitizedXml.length());
            }

            case NODE_COUNT: {
                String activeFlow = null;
                if (selectedSnapshotId != null) {
                    FlowSnapshot snap = com.nexusivr.ai.controller.ServiceRegistry.getFlowSnapshotService().getSnapshot(selectedSnapshotId);
                    if (snap != null) {
                        activeFlow = unifiedAiEngine.resolveStoredFlowToJson(snap.getFlowJson());
                    }
                }
                if (activeFlow == null) {
                    activeFlow = unifiedAiEngine.resolveStoredFlowToJson(unifiedAiEngine.getFlowContextService().getActiveFlow(sessionId));
                }
                if (activeFlow == null || activeFlow.isBlank() || activeFlow.equals("{}")) {
                    return new ChatResponse(sessionId, tenantId, "There is no generated IVR flow in the current session.", MessageRole.ASSISTANT, 1, 0);
                }
                int count = countNodesInJson(activeFlow);
                return new ChatResponse(sessionId, tenantId, "The IVR flow contains " + count + " nodes.", MessageRole.ASSISTANT, 1, 0);
            }

            case NODE_NAMES: {
                String activeFlow = null;
                if (selectedSnapshotId != null) {
                    FlowSnapshot snap = com.nexusivr.ai.controller.ServiceRegistry.getFlowSnapshotService().getSnapshot(selectedSnapshotId);
                    if (snap != null) {
                        activeFlow = unifiedAiEngine.resolveStoredFlowToJson(snap.getFlowJson());
                    }
                }
                if (activeFlow == null) {
                    activeFlow = unifiedAiEngine.resolveStoredFlowToJson(unifiedAiEngine.getFlowContextService().getActiveFlow(sessionId));
                }
                if (activeFlow == null || activeFlow.isBlank() || activeFlow.equals("{}")) {
                    return new ChatResponse(sessionId, tenantId, "There is no generated IVR flow in the current session.", MessageRole.ASSISTANT, 1, 0);
                }
                String list = buildNodeListFromJson(activeFlow);
                return new ChatResponse(sessionId, tenantId, list, MessageRole.ASSISTANT, 1, list.length());
            }

            case FLOW_SUMMARY: {
                String activeFlow = null;
                if (selectedSnapshotId != null) {
                    FlowSnapshot snap = com.nexusivr.ai.controller.ServiceRegistry.getFlowSnapshotService().getSnapshot(selectedSnapshotId);
                    if (snap != null) {
                        activeFlow = unifiedAiEngine.resolveStoredFlowToJson(snap.getFlowJson());
                    }
                }
                if (activeFlow == null) {
                    activeFlow = unifiedAiEngine.resolveStoredFlowToJson(unifiedAiEngine.getFlowContextService().getActiveFlow(sessionId));
                }
                if (activeFlow == null || activeFlow.isBlank() || activeFlow.equals("{}")) {
                    return new ChatResponse(sessionId, tenantId, "There is no generated IVR flow in the current session.", MessageRole.ASSISTANT, 1, 0);
                }
                // Call the LLM to summarize the flow without regenerating it
                String prompt = "Review the following IVR flow structure and write a descriptive summary of what it does, the call routing logic, and queue setup. Do not generate or modify any flow JSON:\n" + activeFlow;
                String summary = chatService.getAiService().generateResponse(prompt, List.of());
                return new ChatResponse(sessionId, tenantId, summary, MessageRole.ASSISTANT, 1, summary.length());
            }

            case GENERATE_FLOW: {
                boolean refineFlag = autoRefine != null ? autoRefine : true;
                Flow flow = unifiedAiEngine.generateFlow(tenantId, sessionId, userMessage,
                        provider, model, temp != null ? temp : -1.0, timeout != null ? timeout : -1,
                        java.util.List.of(), refineFlag);
                return flow;
            }

            case IMPROVE_FLOW: {
                String activeFlow = flowContext;
                if (activeFlow == null || activeFlow.isBlank() || activeFlow.equals("{}")) {
                    if (selectedSnapshotId != null) {
                        FlowSnapshot snap = com.nexusivr.ai.controller.ServiceRegistry.getFlowSnapshotService().getSnapshot(selectedSnapshotId);
                        if (snap != null) {
                            activeFlow = snap.getFlowJson();
                        }
                    }
                }
                if (activeFlow == null || activeFlow.isBlank() || activeFlow.equals("{}")) {
                    activeFlow = unifiedAiEngine.getFlowContextService().getActiveFlow(sessionId);
                }
                if (activeFlow == null || activeFlow.isBlank() || activeFlow.equals("{}")) {
                    return new ChatResponse(sessionId, tenantId, "There is no active IVR flow to improve in the current session.", MessageRole.ASSISTANT, 1, 0);
                }
                return unifiedAiEngine.improveFlow(sessionId, activeFlow, userMessage,
                        provider, model, temp != null ? temp : -1.0, timeout != null ? timeout : -1);
            }

            case VALIDATE_FLOW: {
                String activeFlow = flowContext;
                if (activeFlow == null || activeFlow.isBlank() || activeFlow.equals("{}")) {
                    if (selectedSnapshotId != null) {
                        FlowSnapshot snap = com.nexusivr.ai.controller.ServiceRegistry.getFlowSnapshotService().getSnapshot(selectedSnapshotId);
                        if (snap != null) {
                            activeFlow = snap.getFlowJson();
                        }
                    }
                }
                if (activeFlow == null || activeFlow.isBlank() || activeFlow.equals("{}")) {
                    activeFlow = unifiedAiEngine.getFlowContextService().getActiveFlow(sessionId);
                }
                if (activeFlow == null || activeFlow.isBlank() || activeFlow.equals("{}")) {
                    return new FlowValidationResponse(false, List.of(), 0);
                }
                return unifiedAiEngine.validateFlow(activeFlow);
            }

            case INVOKE_AGENT: {
                String agentId = userMessage != null ? userMessage.replaceFirst("(?i)(use agent:|invoke agent:|agent:)", "").trim().split("\\s+")[0] : "";
                String agentContext = userMessage != null ? userMessage.replaceFirst("(?i)(use agent:|invoke agent:|agent:\\s*" + agentId + "\\s*)", "").trim() : userMessage;
                if (agentContext == null || agentContext.isBlank()) {
                    agentContext = userMessage;
                }
                String advice = specializedAgentService.invoke(agentId, agentContext, provider, model, temp != null ? temp : -1.0, timeout != null ? timeout : -1);
                return new ChatResponse(sessionId, tenantId, advice, MessageRole.ASSISTANT, 1, advice.length());
            }

            case CHAT:
            default:
                // If selectedSnapshotId is present, we must pass its flowJson as context to the LLM
                String customContext = null;
                if (selectedSnapshotId != null) {
                    FlowSnapshot snap = com.nexusivr.ai.controller.ServiceRegistry.getFlowSnapshotService().getSnapshot(selectedSnapshotId);
                    if (snap != null) {
                        customContext = snap.getFlowJson();
                    }
                }
                return chatService.sendMessage(sessionId, tenantId, userMessage, customContext, selectedSnapshotId);
        }
    }

    private int countNodesInJson(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("nodes")) {
                return obj.getAsJsonArray("nodes").size();
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static boolean isFlowReference(String clean) {
        return clean.contains("this flow") || clean.contains("current flow") ||
                clean.contains("existing flow") || clean.contains("the flow") ||
                clean.contains("modify") || clean.contains("change the") ||
                clean.contains("update the") || clean.contains("add to") ||
                clean.contains("remove from") || clean.contains("edit the");
    }

    private static boolean isQuestionFormat(String clean) {
        return clean.startsWith("what ") || clean.startsWith("how ") ||
                clean.startsWith("why ") || clean.startsWith("when ") ||
                clean.startsWith("where ") || clean.startsWith("can ") ||
                clean.startsWith("could ") || clean.startsWith("should ") ||
                clean.startsWith("does ") || clean.startsWith("is ") ||
                clean.startsWith("are ") || clean.startsWith("do ") ||
                clean.startsWith("tell me about ") || clean.startsWith("explain ") ||
                clean.endsWith("?");
    }

    private String buildNodeListFromJson(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("nodes")) {
                JsonArray nodes = obj.getAsJsonArray("nodes");
                if (nodes.size() == 0) return "No nodes found in the flow.";
                StringBuilder sb = new StringBuilder("Here are the nodes in the IVR flow:\n");
                for (int i = 0; i < nodes.size(); i++) {
                    JsonObject n = nodes.get(i).getAsJsonObject();
                    String label = n.has("label") ? n.get("label").getAsString() : 
                                   (n.has("title") ? n.get("title").getAsString() : "Unnamed Node");
                    String type = n.has("type") ? n.get("type").getAsString() : "UNKNOWN";
                    sb.append(String.format("- %s (%s)\n", label, type));
                }
                return sb.toString().trim();
            }
        } catch (Exception ignored) {}
        return "No nodes found in the flow.";
    }

    // ----------------------------------------------------------------
    // Fix #8: Format-aware snapshot → VoiceXML resolution
    // ----------------------------------------------------------------

    /**
     * Resolves snapshot content to VoiceXML. Auto-detects whether the stored
     * content is VoiceXML (returned directly) or JSON (converted via FlowModel).
     * <p>
     * Snapshots normally store canonical VoiceXML (see UnifiedAiEngine.generateFlow),
     * but may fall back to React Flow JSON when VoiceXML is unavailable.
     * The old code blindly called convertJsonToModel() on all snapshot content,
     * causing MalformedJsonException when the content was VoiceXML.
     *
     * @param snapshotContent the raw content from FlowSnapshot.getFlowJson()
     * @return VoiceXML string, or null if conversion fails
     */
    private String resolveSnapshotToVxml(String snapshotContent) {
        if (snapshotContent == null || snapshotContent.isBlank()) {
            return null;
        }
        String trimmed = snapshotContent.trim();

        // Path A: Content is already VoiceXML — return directly
        if (trimmed.startsWith("<?xml") || trimmed.startsWith("<vxml")) {
            logger.info("[AiOperationRouter] Snapshot contains VoiceXML. Returning directly.");
            return trimmed;
        }

        // Path B: Content is JSON — convert to FlowModel, then export to VoiceXML
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                FlowModel snapModel = FlowContextService.convertJsonToModel(snapshotContent);
                if (snapModel != null) {
                    String xml = new ModelToVxmlExporter().export(snapModel);
                    logger.info("[AiOperationRouter] Converted snapshot JSON → FlowModel → VoiceXML.");
                    return xml;
                }
            } catch (Exception e) {
                logger.warn("[AiOperationRouter] Failed to convert snapshot JSON to VoiceXML: {}", e.getMessage());
            }
        }

        // Path C: Content may have markdown fences — try VoiceXML conversion
        try {
            FlowModel snapModel = FlowContextService.convertVxmlToModel(snapshotContent);
            if (snapModel != null) {
                String xml = new ModelToVxmlExporter().export(snapModel);
                logger.info("[AiOperationRouter] Converted snapshot (wrapped VXML) → FlowModel → VoiceXML.");
                return xml;
            }
        } catch (Exception e) {
            logger.warn("[AiOperationRouter] Failed to parse snapshot content as VoiceXML: {}", e.getMessage());
        }

        logger.error("[AiOperationRouter] EXPORT_XML failed: snapshot content is neither valid VoiceXML nor parseable JSON. First 40 chars: {}",
                trimmed.substring(0, Math.min(40, trimmed.length())));
        return null;
    }
}
