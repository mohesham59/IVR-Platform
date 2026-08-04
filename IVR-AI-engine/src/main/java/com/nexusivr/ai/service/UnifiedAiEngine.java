package com.nexusivr.ai.service;

import com.nexusivr.ai.ai.*;
import com.nexusivr.ai.ai.optimization.*;
import com.nexusivr.ai.config.GlobalAiConfig;
import com.nexusivr.ai.dto.ChatResponse;
import com.nexusivr.ai.dto.common.FlowDto;
import com.nexusivr.ai.dto.common.ValidationIssueDto;
import com.nexusivr.ai.dto.common.ValidationSeverity;
import com.nexusivr.ai.dto.response.FlowImprovementResponse;
import com.nexusivr.ai.dto.response.FlowValidationResponse;
import com.nexusivr.ai.dto.response.QuotaWarning;
import com.nexusivr.ai.dto.patch.*;
import com.nexusivr.ai.model.Flow;
import com.nexusivr.ai.model.Message;
import com.nexusivr.ai.model.MessageRole;
import com.nexusivr.ai.model.FlowSnapshot;
import com.nexusivr.ai.model.flow.FlowModel;
import com.nexusivr.ai.model.flow.FlowNode;
import com.nexusivr.ai.model.flow.FlowNodeType;
import com.nexusivr.ai.model.flow.FlowConnection;
import com.nexusivr.ai.controller.ServiceRegistry;
import com.nexusivr.ai.service.exception.ProviderException;
import com.nexusivr.ai.service.VxmlToModelConverter;
import com.nexusivr.ai.service.LlmResponseNormalizer;
import com.nexusivr.ai.service.LlmResponseNormalizationException;
import com.nexusivr.ai.service.FlowValidationOrchestrator;
import com.nexusivr.ai.util.XmlLogFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Centrally manages the entire AI pipeline, acting as the orchestrator for
 * Chat turn messaging, flow generation, validation, suggestions, and auto-repair.
 */
public class UnifiedAiEngine {

    private static final Logger logger = LoggerFactory.getLogger(UnifiedAiEngine.class);

    private final ProviderManager providerManager;
    private final PromptBuilder promptBuilder;
    private final FlowContextService flowContextService;
    private final PromptRefinerService promptRefinerService;
    private final VxmlValidator vxmlValidator;
    private final VxmlToModelConverter vxmlToModelConverter;
    private final ModelFlowValidator modelFlowValidator;
    private final ModelAutoRepair modelAutoRepair;
    private final ModelToFlowRenderer modelToFlowRenderer;
    private final ModelToVxmlExporter modelToVxmlExporter;
    private final DomainFlowGenerator domainFlowGenerator;
    private final FlowPatchApplier flowPatchApplier;
    private final FlowModelValidator flowModelValidator;
    private final FlowModelAutoRepair flowModelAutoRepair;
    private final FlowValidationOrchestrator flowValidationOrchestrator;

    public UnifiedAiEngine(ProviderManager providerManager, PromptBuilder promptBuilder,
                            FlowContextService flowContextService) {
        this(providerManager, promptBuilder, flowContextService, new PromptRefinerService(providerManager));
    }

    public UnifiedAiEngine(ProviderManager providerManager, PromptBuilder promptBuilder,
                            FlowContextService flowContextService, PromptRefinerService promptRefinerService) {
        this.providerManager = providerManager;
        this.promptBuilder = promptBuilder;
        this.flowContextService = flowContextService;
        this.promptRefinerService = promptRefinerService;
        this.vxmlValidator = new VxmlValidator();
        this.vxmlToModelConverter = new VxmlToModelConverter();
        this.modelFlowValidator = new ModelFlowValidator();
        this.modelAutoRepair = new ModelAutoRepair();
        this.modelToFlowRenderer = new ModelToFlowRenderer();
        this.modelToVxmlExporter = new ModelToVxmlExporter();
        this.domainFlowGenerator = new DomainFlowGenerator();
        this.flowPatchApplier = new FlowPatchApplier();
        this.flowModelValidator = new FlowModelValidator();
        this.flowModelAutoRepair = new FlowModelAutoRepair();
        this.flowValidationOrchestrator = new FlowValidationOrchestrator(flowModelValidator, flowModelAutoRepair, 10);
        SessionMemoryStore.setModelToFlowRenderer(modelToFlowRenderer);
    }

    /**
     * Generates a concise, descriptive flow title from the user description
     * and detected domain.
     */
    private String generateDescriptiveTitle(String description, String domain) {
        if (description == null || description.isBlank()) return "IVR Flow";
        String clean = com.nexusivr.ai.util.TitleSanitizer.sanitize(description);
        if (clean.length() > 60) clean = clean.substring(0, 57) + "...";

        String domainLabel = domain != null && !domain.isBlank() ? domain.trim() : "Business";
        return domainLabel.substring(0, 1).toUpperCase() + domainLabel.substring(1) + " IVR";
    }

    /**
     * Generates a brand new IVR flow from description prompt.
     */
    public Flow generateFlow(UUID tenantId, UUID sessionId, String description,
                             String provider, String model, double temp, int timeout) {
        List<Message> history = List.of();
        if (sessionId != null) {
            try {
                UUID tId = tenantId != null ? tenantId : UUID.fromString("00000000-0000-0000-0000-000000000000");
                history = ServiceRegistry.getMessageDao().findBySessionId(sessionId, tId);
            } catch (Exception ignored) {}
        }
        return generateFlow(tenantId, sessionId, description, provider, model, temp, timeout, history);
    }

    public Flow generateFlow(UUID tenantId, UUID sessionId, String description,
                             String provider, String model, double temp, int timeout,
                             List<Message> history) {
        return generateFlow(tenantId, sessionId, description, provider, model, temp, timeout, history, null);
    }

    public Flow generateFlow(UUID tenantId, UUID sessionId, String description,
                             String provider, String model, double temp, int timeout,
                             List<Message> history, ProgressListener progressListener) {
        logger.info("[UnifiedAiEngine] Starting FLOW GENERATION pipeline. Session: {}. Selected Provider: {}, refinementMode: always-on", sessionId, provider);
        notifyProgress(progressListener, "understanding", "Understanding user request...");

        List<QuotaWarning> quotaWarnings = new ArrayList<>();

        // 1. Resolve Provider and parameters
        double activeTemp = temp >= 0 ? temp : GlobalAiConfig.getInstance().getTemperature();
        int activeTimeout = timeout > 0 ? timeout : GlobalAiConfig.getInstance().getTimeout();
        String activeProvider = (provider != null) ? provider : GlobalAiConfig.getInstance().getProvider();
        String activeModel = (model != null) ? model : GlobalAiConfig.getInstance().getModel();

        String generationModel = activeModel;
        if ("groq".equals(activeProvider)) {
            generationModel = com.nexusivr.ai.config.LlmConfig.getGroqGenerationModel();
        } else if ("gemini".equals(activeProvider)) {
            generationModel = com.nexusivr.ai.config.LlmConfig.getGeminiGenerationModel();
        }
        logger.info("[UnifiedAiEngine] Generation model: {}", generationModel);

        String detectedDomain = DomainDetector.detect(description);
        logger.info("[UnifiedAiEngine] Lightweight domain hint (for logging/title only): '{}' -> '{}'", description, detectedDomain);
        notifyProgress(progressListener, "template", "Selecting template/domain (" + detectedDomain + ")...");
        if (sessionId != null && detectedDomain != null && !"generic".equalsIgnoreCase(detectedDomain)) {
            SessionMemoryStore.setDomain(sessionId, detectedDomain);
        }

        // 2. Two-pass generation: Pass 1 = Prompt Refiner (LLM), Pass 2 = Flow Generator (LLM)
        // Pass 1 prompt refinement is a fixed backend default and runs unconditionally for all flow generations.
        String normalizedPrompt = description != null ? description.trim().toLowerCase() : "";
        String generationInput = description;
        String refinedSpec = null;
        boolean pass1Skipped = false;
        boolean pass1CacheHit = false;

        String pass1Reason = PromptCompletenessChecker.getReason(description);
        logger.info("[UnifiedAiEngine] Pass 1 heuristic status: {}", pass1Reason);

        String pass1CacheKey = buildCacheKey(tenantId, description, activeProvider, generationModel, detectedDomain);
        String cachedSpec = RefinedSpecCache.get(pass1CacheKey);
        if (cachedSpec != null) {
            pass1CacheHit = true;
            refinedSpec = cachedSpec;
            generationInput = cachedSpec;
            logger.info("[UnifiedAiEngine] Pass 1 cache hit. Reusing refined spec for Pass 2.");
        } else {
            logger.info("[UnifiedAiEngine] Pass 1 triggered: running LLM prompt refinement with historySize={}, domain='{}'...",
                    history != null ? history.size() : 0, detectedDomain);
            notifyProgress(progressListener, "analysis", "Business analysis & prompt refinement (Pass 1)...");
            try {
                refinedSpec = promptRefinerService.refine(description, activeProvider, generationModel, activeTemp, activeTimeout, history, detectedDomain);
                if (refinedSpec != null && !refinedSpec.isBlank() && PromptRefinerService.isValidRefinedJsonSpec(refinedSpec)) {
                    generationInput = refinedSpec;
                    RefinedSpecCache.put(pass1CacheKey, refinedSpec);
                } else {
                    logger.warn("[UnifiedAiEngine] Pass 1 returned unrefined or invalid JSON spec. Forwarding original raw prompt directly to Pass 2.");
                    generationInput = description;
                    refinedSpec = null;
                }
            } catch (Exception e) {
                logger.warn("[UnifiedAiEngine] Pass 1 (Prompt Refiner) failed: {}. Falling back to raw prompt for Pass 2.", e.getMessage());
                generationInput = description;
                refinedSpec = null;
            }
        }

// 3. Pass 2: Generate raw response using the (possibly refined) prompt
        notifyProgress(progressListener, "planning", "Planning flow structure & features...");
        String systemInstruction = com.nexusivr.ai.config.GlobalAiConfig.getInstance().getSystemPrompt();
        if (systemInstruction == null || systemInstruction.equals(com.nexusivr.ai.ai.PromptBuilder.DEFAULT_SYSTEM_INSTRUCTION)) {
            systemInstruction = PromptBuilder.FLOW_GENERATOR_SYSTEM_INSTRUCTION;
        }

        String flowDomain = detectedDomain;
        if (refinedSpec != null && !refinedSpec.isBlank()) {
            try {
                com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(refinedSpec).getAsJsonObject();
                if (obj.has("business_domain") && !obj.get("business_domain").getAsString().isBlank()) {
                    String specDomain = obj.get("business_domain").getAsString().trim().toLowerCase();
                    if (!specDomain.isBlank() && !"generic".equalsIgnoreCase(specDomain)) {
                        flowDomain = specDomain;
                    }
                }
            } catch (Exception ignored) {}
        }
        if (flowDomain == null || flowDomain.isBlank()) {
            flowDomain = "generic";
        }
        if (sessionId != null && !"generic".equalsIgnoreCase(flowDomain)) {
            SessionMemoryStore.setDomain(sessionId, flowDomain);
        }

        String pass2UserPrompt = generationInput;
        String pass2CacheKey = buildCacheKey(tenantId, pass2UserPrompt, activeProvider, generationModel, flowDomain);

         // Check SemanticCache for Pass 2 response
         String cachedPass2Response = SemanticCache.getInstance().get(pass2CacheKey);
         String rawResponse;
         String actualProviderUsed = activeProvider;
         if (cachedPass2Response != null) {
             logger.info("[UnifiedAiEngine] Pass 2 SemanticCache hit. Reusing cached response.");
             rawResponse = cachedPass2Response;
         } else {
             logger.info("[AUDIT] Stage 1 (Pass 1 - Refiner): skipped={}, cacheHit={}, reason={}", pass1Skipped, pass1CacheHit, pass1Reason);
             logger.info("[AUDIT] Stage 1 Output (Pass 1 refined spec / raw prompt): {}", generationInput);
             logger.info("[AUDIT] Stage 2 (Pass 2 - Generator): User Prompt = {}, flowDomain = {}", pass2UserPrompt, flowDomain);
             logger.info("[UnifiedAiEngine] Expected Format: VoiceXML");
             logger.info("[UnifiedAiEngine] Generation attempt 1 of 2 (malformed output triggers one LLM retry, validation failures repaired locally).");
             logger.info("[AUDIT] Stage 3: LLM Request sent using provider = {}, model = {}", activeProvider, generationModel);

             notifyProgress(progressListener, "generating", "Generating VoiceXML structure (Pass 2)...");
             AiResponse llmResponse;
              try {
                  llmResponse = providerManager.executeWithRetryAndFallback(
                          activeProvider, generationModel, activeTemp, activeTimeout,
                          systemInstruction, pass2UserPrompt, "GENERATE_FLOW", quotaWarnings, flowDomain,
                          true
                  );
                  actualProviderUsed = llmResponse.getActualProviderUsed();
              } catch (ProviderException e) {
                  logger.error("[UnifiedAiEngine] Provider failure during flow generation: {}", e.getMessage());
                  throw e;
              }

                rawResponse = llmResponse.getContent();
                logger.info("[AUDIT] Stage 4: Raw LLM Response (provider={}, actualProvider={}) = {}", activeProvider, llmResponse.getActualProviderUsed(), rawResponse);

                if (isMalformedLlmOutput(rawResponse)) {
                    String malformationReason = diagnoseMalformation(rawResponse);
                    logger.info("[AUDIT] Stage 3b: LLM retry triggered due to malformed/truncated output. Reason: {}", malformationReason);

                    String retrySystemInstruction = systemInstruction;
                    if ("VXML_FIELD_NOT_STRING".equals(malformationReason)) {
                        retrySystemInstruction = systemInstruction + "\n\n" +
                                "RETRY REMINDER: Your previous response had the wrong shape. The \"vxml\" field MUST be a single JSON string containing VoiceXML text (starting with <?xml...?>), NOT a nested JSON object. Return only {\"vxml\": \"<?xml version=\\\"1.0\\\"...>...</vxml>\"}.";
                    } else if ("INVALID_BLOCK_WITH_CHOICE".equals(malformationReason)) {
                        retrySystemInstruction = systemInstruction + "\n\n" +
                                "RETRY REMINDER: Your previous VoiceXML response was invalid because navigation <choice> tags were placed inside a <block>. Navigation menus MUST use the VoiceXML <menu> element with <choice> children, NOT <block> with <choice> (e.g. use <form id=\"menu\"><menu><prompt>...</prompt><choice next=\"#b\">1</choice></menu></form>).";
                    } else if (malformationReason.startsWith("UNCLOSED_") || malformationReason.startsWith("XML_SYNTAX_ERROR")) {
                        retrySystemInstruction = systemInstruction + "\n\n" +
                                "RETRY REMINDER: Your previous VoiceXML response contained malformed or unclosed XML tags (Reason: " + malformationReason + "). Return fully valid, well-formed VoiceXML with properly matched closing tags (e.g. </block>, </form>, </vxml>).";
                    } else {
                        retrySystemInstruction = systemInstruction + "\n\n" +
                                "RETRY REMINDER: Your previous response was malformed (Reason: " + malformationReason + "). Please return valid, well-formed VoiceXML.";
                    }

                    try {
                        logger.info("[UnifiedAiEngine] Generation attempt 2 of 2 (dispatched after detecting malformed output).");
                        llmResponse = providerManager.executeWithRetryAndFallback(
                                activeProvider, generationModel, activeTemp, activeTimeout,
                                retrySystemInstruction, pass2UserPrompt, "GENERATE_FLOW", quotaWarnings, flowDomain,
                                true
                        );
                        actualProviderUsed = llmResponse.getActualProviderUsed();
                    } catch (ProviderException e) {
                        logger.error("[UnifiedAiEngine] LLM retry (attempt 2 of 2) failed: {}", e.getMessage());
                        throw e;
                    }
                    rawResponse = llmResponse.getContent();
                }
            }

        String normalizedVxml;
        notifyProgress(progressListener, "validating", "Validating VoiceXML & structural integrity...");
        try {
            normalizedVxml = LlmResponseNormalizer.normalize(rawResponse);
        } catch (LlmResponseNormalizationException e) {
            // Normalization failed on attempt 1. Dispatch corrective retry before giving up.
            logger.warn("[UnifiedAiEngine] Normalization failed on attempt 1: {}. Dispatching corrective LLM retry.", e.getMessage());
            normalizedVxml = null;

            String normRetryInstruction = systemInstruction + "\n\n" +
                    "RETRY REMINDER: Your previous response could not be parsed. Error: \"" + e.getMessage() + "\". " +
                    "Return ONLY a valid JSON object with a single \"vxml\" key containing a VoiceXML string. " +
                    "Example: {\"vxml\": \"<?xml version=\\\"1.0\\\"?>\\n<vxml version=\\\"2.1\\\">...</vxml>\"}";

            try {
                logger.info("[UnifiedAiEngine] Generation attempt 2 of 2 (dispatched after normalization failure).");
                AiResponse normRetryResponse = providerManager.executeWithRetryAndFallback(
                        activeProvider, generationModel, activeTemp, activeTimeout,
                        normRetryInstruction, pass2UserPrompt, "GENERATE_FLOW", quotaWarnings, flowDomain,
                        true
                );
                actualProviderUsed = normRetryResponse.getActualProviderUsed();
                rawResponse = normRetryResponse.getContent();
                normalizedVxml = LlmResponseNormalizer.normalize(rawResponse);
            } catch (ProviderException retryEx) {
                logger.error("[UnifiedAiEngine] Corrective retry after normalization failure also failed (provider): {}", retryEx.getMessage());
            } catch (LlmResponseNormalizationException retryNormEx) {
                logger.error("[UnifiedAiEngine] Corrective retry after normalization failure: normalization still failed: {}", retryNormEx.getMessage());
            } catch (Exception retryEx) {
                logger.error("[UnifiedAiEngine] Corrective retry after normalization failure failed (unexpected): {}", retryEx.getMessage());
            }

            if (normalizedVxml == null) {
                String errorMsg = "AI generation failed: LLM output could not be normalized into VoiceXML for domain: " + detectedDomain +
                        ". Normalization error: " + e.getMessage() + ". Both attempts failed.";
                logger.error("[AUDIT] Flow Generation failed at normalization after both attempts. domain={}, error={}", detectedDomain, e.getMessage());
                throw new ProviderException("all", errorMsg, ProviderException.FailureReason.PROVIDER_ERROR);
            }
        }
        logger.info("[AUDIT] Stage 4c: Normalized VoiceXML length={} chars.\n========== GENERATED VXML ==========\n{}\n========== END VXML ==========", normalizedVxml.length(), XmlLogFormatter.prettyPrintWithLineNumbers(normalizedVxml));

        // 4. Parse normalized VoiceXML into Internal Flow Model (single source of truth).
        notifyProgress(progressListener, "converting", "Converting VoiceXML to canvas FlowModel...");
        FlowModel candidateModel = parseNormalizedVoiceXml(normalizedVxml, detectedDomain, activeProvider, "GENERATE_FLOW");
        String canonicalVoicexml = null;
        FlowModel finalModel = null;
        if (candidateModel != null) {
            canonicalVoicexml = normalizedVxml;
        }

        // 4a. Corrective LLM retry: if VXML normalized OK but XML parse/conversion failed
        //     (e.g., unclosed <block> tag), dispatch one corrective retry before giving up.
        //     This is the "second gate" retry — the first gate (isMalformedLlmOutput) checks
        //     the raw response; this gate catches parse failures that only surface during
        //     full DOM parsing in VxmlToModelConverter.
        if (candidateModel == null) {
            logger.warn("[UnifiedAiEngine] Parse failure on attempt 1: VoiceXML normalized but VxmlToModelConverter failed. " +
                    "Dispatching corrective LLM retry (attempt 2 of 2).");

            // Diagnose the specific parse error for the corrective instruction
            String parseErrorDetail = "unknown XML parse error";
            try {
                vxmlToModelConverter.convert(normalizedVxml);
            } catch (Exception diagEx) {
                parseErrorDetail = diagEx.getMessage();
            }

            String specificCondHint = "";
            if (parseErrorDetail != null) {
                String errLower = parseErrorDetail.toLowerCase();
                if (errLower.contains("must not contain the '<' character") || errLower.contains("cond") || errLower.contains("if") || errLower.contains("else") || errLower.contains("</else>")) {
                    specificCondHint = "\n\nCRITICAL SPECIFIC FIX REQUIRED FOR CONDITIONAL LOGIC:\n" +
                            "- Unescaped '<' in attribute: Your previous response contained an unescaped '<' inside an <if cond=\"...\"> attribute. In XML, raw '<' inside attributes is ILLEGAL. You MUST escape less-than as &lt; and greater-than as &gt; (e.g., cond=\"attempts &lt; 3\"). NEVER write cond=\"attempts < 3\".\n" +
                            "- Incorrect <else> paired tag: VoiceXML 2.1 requires <else/> to be a self-closing empty tag: <else/>. NEVER write paired <else>...</else> tags. Any else-branch elements go as siblings AFTER <else/> and before </if>.\n" +
                            "Fix ALL instances of unescaped '<' inside cond attributes and paired <else></else> tags, and resend complete corrected VoiceXML.";
                }
            }

            String correctiveSystemInstruction = systemInstruction + "\n\n" +
                    "RETRY REMINDER (CRITICAL): Your previous VoiceXML response contained malformed XML that failed parsing. " +
                    "Error: \"" + parseErrorDetail + "\". " +
                    "Ensure EVERY element has a properly matched closing tag. Specifically:\n" +
                    "- Every <block> MUST have a </block> BEFORE its parent </form>\n" +
                    "- Every <form> MUST have a </form> BEFORE its parent </vxml>\n" +
                    "- Every <field> MUST have a </field>\n" +
                    "- Every <if> MUST have a </if>\n" +
                    "- Escape '<' as &lt; in all cond=\"...\" attributes\n" +
                    "- Use self-closing <else/> inside <if> blocks" +
                    specificCondHint + "\n\n" +
                    "Return fully valid, well-formed VoiceXML.";

            try {
                logger.info("[UnifiedAiEngine] Generation attempt 2 of 2 (dispatched after XML parse failure in VxmlToModelConverter).");
                AiResponse retryLlmResponse = providerManager.executeWithRetryAndFallback(
                        activeProvider, generationModel, activeTemp, activeTimeout,
                        correctiveSystemInstruction, pass2UserPrompt, "GENERATE_FLOW", quotaWarnings, flowDomain,
                        true
                );
                actualProviderUsed = retryLlmResponse.getActualProviderUsed();
                String retryRawResponse = retryLlmResponse.getContent();

                String retryNormalizedVxml;
                try {
                    retryNormalizedVxml = LlmResponseNormalizer.normalize(retryRawResponse);
                } catch (LlmResponseNormalizationException normEx) {
                    logger.error("[UnifiedAiEngine] Corrective retry (attempt 2 of 2): normalization also failed: {}", normEx.getMessage());
                    retryNormalizedVxml = null;
                }

                if (retryNormalizedVxml != null) {
                    FlowModel retryModel = parseNormalizedVoiceXml(retryNormalizedVxml, detectedDomain, activeProvider, "GENERATE_FLOW");
                    if (retryModel != null) {
                        logger.info("[UnifiedAiEngine] Corrective retry (attempt 2 of 2): SUCCESS. Parsed {} nodes from corrected VXML.", retryModel.getNodes().size());
                        candidateModel = retryModel;
                        normalizedVxml = retryNormalizedVxml;
                        canonicalVoicexml = retryNormalizedVxml;
                        rawResponse = retryRawResponse;
                    } else {
                        logger.error("[UnifiedAiEngine] Corrective retry (attempt 2 of 2): VXML still failed to parse after LLM correction.");
                    }
                }
            } catch (ProviderException retryEx) {
                logger.error("[UnifiedAiEngine] Corrective retry (attempt 2 of 2) failed with provider error: {}", retryEx.getMessage());
            } catch (Exception retryEx) {
                logger.error("[UnifiedAiEngine] Corrective retry (attempt 2 of 2) failed with unexpected error: {}", retryEx.getMessage());
            }
        }

        // Final gate: if candidateModel is still null after the corrective retry, give up.
        if (candidateModel == null) {
            String vxmlSnippet = normalizedVxml.length() > 500
                    ? normalizedVxml.substring(0, 500) + "..."
                    : normalizedVxml;
            String errorMsg = "AI generation failed: normalized VoiceXML could not be parsed into FlowModel for domain: " + detectedDomain +
                    ". Provider: " + activeProvider + ". VXML length: " + normalizedVxml.length() + " chars. First 500 chars: " + vxmlSnippet +
                    ". Both generation attempts (original + corrective retry) failed. Please retry or contact support if the issue persists.";
            logger.error("[AUDIT] Flow Generation failed after both attempts. Throwing ProviderException. domain={}, provider={}, vxmlLength={}, first500={}",
                    detectedDomain, activeProvider, normalizedVxml.length(), vxmlSnippet);
            throw new ProviderException("all", errorMsg, ProviderException.FailureReason.PROVIDER_ERROR);
        }

        // 4b. Post-generation feature completeness check: verify all requested features/departments are present
        List<String> requiredFeatures = extractRequestedFeatures(description, refinedSpec);
        List<String> missingFeatures = getMissingFeatures(candidateModel, requiredFeatures);

        if (!missingFeatures.isEmpty()) {
            FlowValidationResponse preCheckVal = modelFlowValidator.validate(candidateModel);
            if (preCheckVal.getScore() >= 70 && missingFeatures.size() <= 2) {
                logger.info("[UnifiedAiEngine] Candidate model is valid with score={}. Missing {} minor feature(s): {}. Skipping corrective 3rd LLM call to optimize generation speed.",
                        preCheckVal.getScore(), missingFeatures.size(), missingFeatures);
            } else {
                logger.warn("[UnifiedAiEngine] Post-generation completeness check failed. Candidate model is missing {} feature(s): {}",
                        missingFeatures.size(), missingFeatures);
                logger.info("[AUDIT] Stage 3c: LLM retry triggered due to missing requested features: {}", missingFeatures);

                String featureRetryInstruction = systemInstruction + "\n\n" +
                        "RETRY REMINDER: Your previous VoiceXML response omitted the following explicitly required features/departments: " +
                        String.join(", ", missingFeatures) + ". " +
                        "You MUST regenerate the complete VoiceXML incorporating forms/menus for ALL of the following required features: " +
                        String.join(", ", requiredFeatures) + ". Ensure every single feature has a dedicated form or menu node.";

                try {
                    AiResponse retryResponse = providerManager.executeWithRetryAndFallback(
                            activeProvider, generationModel, activeTemp, activeTimeout,
                            featureRetryInstruction, pass2UserPrompt, "GENERATE_FLOW", quotaWarnings, flowDomain,
                            true
                    );
                    if (retryResponse != null && retryResponse.getContent() != null && !retryResponse.getContent().isBlank()) {
                        String retryVxml = LlmResponseNormalizer.normalize(retryResponse.getContent());
                        FlowModel retryModel = parseNormalizedVoiceXml(retryVxml, detectedDomain, activeProvider, "GENERATE_FLOW");
                        if (retryModel != null && !retryModel.getNodes().isEmpty()) {
                            List<String> retryMissing = getMissingFeatures(retryModel, requiredFeatures);
                            if (retryMissing.size() < missingFeatures.size()) {
                                logger.info("[UnifiedAiEngine] Feature completeness LLM retry improved feature count! Missing features reduced from {} to {}.",
                                        missingFeatures.size(), retryMissing.size());
                                candidateModel = retryModel;
                                normalizedVxml = retryVxml;
                                canonicalVoicexml = retryVxml;
                                missingFeatures = retryMissing;
                            } else {
                                logger.warn("[UnifiedAiEngine] Feature completeness LLM retry did not improve missing features (still missing {}). Retaining candidate model.", retryMissing.size());
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("[UnifiedAiEngine] Feature completeness LLM retry failed with exception: {}. Continuing with candidate model.", e.getMessage());
                }
            }
        }

        // 5. Validate Internal Flow Model (single validation step)
        FlowValidationResponse validation = modelFlowValidator.validate(candidateModel);
        List<String> droppedFeatures = new ArrayList<>();
        if (!missingFeatures.isEmpty()) {
            droppedFeatures.addAll(missingFeatures);
        }
        int finalScore = validation.getScore();
        if (validation.isValid()) {
            logger.info("[UnifiedAiEngine] Model Validation Result: {}. Score={}, Errors={}, Warnings={}, Nodes={}, Connections={}.",
                    validation.getStatus(), validation.getScore(), validation.getErrorCount(), validation.getWarningCount(),
                    candidateModel.getNodes().size(), candidateModel.getConnections().size());
            finalModel = candidateModel;
            logger.info("[UnifiedAiEngine] Model accepted. Nodes={}, Connections={}",
                    candidateModel.getNodes().size(), candidateModel.getConnections().size());
            logger.info("[UnifiedAiEngine] FLOW MODEL SUMMARY\n{}", buildModelSummary(candidateModel));
        } else {
            // 6. Repair Internal Flow Model locally — never call the LLM again
            logger.info("[UnifiedAiEngine] Model Validation Result: {}. Errors={}, Warnings={}. Action: Attempt ModelAutoRepair locally. No retry.",
                    validation.getStatus(), validation.getErrorCount(), validation.getWarningCount());
            List<String> requestedFeatureKeywords = extractRequestedFeatures(description, generationInput);
            FlowModel repairedModel = modelAutoRepair.repair(candidateModel, validation, requestedFeatureKeywords, droppedFeatures);
            FlowValidationResponse repairedValidation = modelFlowValidator.validate(repairedModel);
            finalScore = repairedValidation.getScore();

            if (repairedValidation.isValid()) {
                logger.info("[UnifiedAiEngine] Model Repair Result: SUCCESS. Nodes={}, Connections={}.",
                        repairedModel.getNodes().size(), repairedModel.getConnections().size());
                finalModel = repairedModel;
                canonicalVoicexml = modelToVxmlExporter.export(repairedModel);
                logger.info("[UnifiedAiEngine] ModelAutoRepair succeeded locally.");
            } else {
                logger.warn("[UnifiedAiEngine] ModelAutoRepair did not resolve all validation errors (Errors={}). Attempting single corrective LLM retry.",
                        repairedValidation.getErrorCount());
                String validationErrorDetails = repairedValidation.getIssues().stream()
                        .filter(i -> i.getSeverity() == com.nexusivr.ai.dto.common.ValidationSeverity.ERROR)
                        .map(com.nexusivr.ai.dto.common.ValidationIssueDto::getMessage)
                        .collect(Collectors.joining("; "));
                String repairRetryInstruction = systemInstruction + "\n\n" +
                        "CORRECTIVE RETRY REQUIRED: Your previous VoiceXML response produced a flow model with structural validation errors: " +
                        validationErrorDetails + ". " +
                        "Please regenerate the complete, valid VoiceXML ensuring every form is reachable, leaf forms have valid exit paths, and transfer nodes only use 'success' or 'fail' exit paths.";
                try {
                    AiResponse retryResp = providerManager.executeWithRetryAndFallback(
                            activeProvider, generationModel, activeTemp, activeTimeout,
                            repairRetryInstruction, pass2UserPrompt, "GENERATE_FLOW", quotaWarnings, flowDomain, true
                    );
                    if (retryResp != null && retryResp.getContent() != null && !retryResp.getContent().isBlank()) {
                        String retryVxml = LlmResponseNormalizer.normalize(retryResp.getContent());
                        FlowModel retryModel = parseNormalizedVoiceXml(retryVxml, detectedDomain, activeProvider, "GENERATE_FLOW");
                        if (retryModel != null && !retryModel.getNodes().isEmpty()) {
                            FlowModel secondRepaired = modelAutoRepair.repair(retryModel, modelFlowValidator.validate(retryModel), requestedFeatureKeywords, droppedFeatures);
                            FlowValidationResponse secondVal = modelFlowValidator.validate(secondRepaired);
                            if (secondVal.isValid()) {
                                logger.info("[UnifiedAiEngine] Corrective LLM retry succeeded! Nodes={}, Connections={}.",
                                        secondRepaired.getNodes().size(), secondRepaired.getConnections().size());
                                finalModel = secondRepaired;
                                canonicalVoicexml = retryVxml;
                                finalScore = secondVal.getScore();
                            }
                        }
                    }
                } catch (Exception retryEx) {
                    logger.warn("[UnifiedAiEngine] Corrective LLM retry exception: {}. Proceeding to failure throw.", retryEx.getMessage());
                }

                if (finalModel == null) {
                    String errorMsg = "AI generation failed after local repair attempts for domain: " + detectedDomain +
                            ". Please retry or contact support if the issue persists.";
                    logger.error("[AUDIT] Flow Generation failed after local repair. Throwing ProviderException. domain={}", detectedDomain);
                    throw new ProviderException("all", errorMsg, ProviderException.FailureReason.PROVIDER_ERROR);
                }
            }
        }

        // Cache the Pass 2 response only after it has produced a valid, repairable model
        SemanticCache.getInstance().put(pass2CacheKey, rawResponse);

        // 8. Render Internal Flow Model to React Flow JSON (frontend format)
        String finalFlowJson = modelToFlowRenderer.render(finalModel);
        logger.info("[UnifiedAiEngine] Converter Stage: FlowModel → React Flow JSON. Status: SUCCESS. Nodes={}.\n{}",
                getJsonArraySize(finalFlowJson, "nodes"), buildModelSummary(finalModel));

        // Inject metadata into JSON
        try {
            JsonObject rootObj = JsonParser.parseString(finalFlowJson).getAsJsonObject();
            rootObj.addProperty("name", finalModel.getName() != null ? finalModel.getName() : (description.length() > 30 ? description.substring(0, 30) + "..." : description));
            rootObj.addProperty("description", finalModel.getDescription() != null ? finalModel.getDescription() : description);
            finalFlowJson = rootObj.toString();
        } catch (Exception e) {
            logger.warn("Failed to inject metadata into final JSON: {}", e.getMessage());
        }

        // Parse and log final node/edge counts sent to builder
        int finalNodeCount = getJsonArraySize(finalFlowJson, "nodes");
        int finalEdgeCount = getJsonArraySize(finalFlowJson, "edges");
        logger.info("[AUDIT] Stage 12: Final Output Sent to IVR Builder. Nodes = {}, Edges = {}", finalNodeCount, finalEdgeCount);

        // 6. Save context and create snapshot V1
        // Store the Internal Flow Model as the single source of truth in session memory.
        // Snapshots store VoiceXML (canonical format).
        UUID flowId = UUID.randomUUID();
        if (sessionId != null) {
            flowContextService.saveActiveFlow(sessionId, finalModel);
            ServiceRegistry.getFlowSnapshotService().createSnapshot(sessionId, flowId, canonicalVoicexml != null ? canonicalVoicexml : finalFlowJson);
        }

        // Return flow entity model
        Flow flow = new Flow();
        flow.setId(flowId);
        flow.setTenantId(tenantId);
        String rawFlowName = (finalModel.getName() != null && !FlowDraftService.isSpokenGreeting(finalModel.getName()))
                ? finalModel.getName()
                : generateDescriptiveTitle(description, detectedDomain);
        flow.setName(com.nexusivr.ai.util.TitleSanitizer.sanitize(rawFlowName));

        flow.setDescription(description);
        flow.setFlowJson(finalFlowJson);
        flow.setStatus("DRAFT");
        flow.setQuotaWarnings(quotaWarnings);
        flow.setSelectedProvider(activeProvider);
        flow.setActualProviderUsed(actualProviderUsed);
        boolean flowFallbackUsed = activeProvider != null && actualProviderUsed != null && !activeProvider.equalsIgnoreCase(actualProviderUsed);
        flow.setFallbackUsed(flowFallbackUsed);
        boolean isTemplateFallback = "template-generator".equalsIgnoreCase(actualProviderUsed);
        if (isTemplateFallback) {
            flow.setTemplateFallback(true);
            flow.setFallbackNotice("Template Fallback Mode: Generated using built-in template rules because AI providers are temporarily offline.");
        }
        if (flowFallbackUsed) {
            flow.setFallbackReason(activeProvider + " failed. Response generated using " + actualProviderUsed + ".");
        }
        flow.setRefinedPrompt(refinedSpec);
        flow.setDroppedFeatures(droppedFeatures);
        flow.setValidationScore(finalScore);

        logger.info("[UnifiedAiEngine] Flow generation complete. Session: {}. Status: {}", sessionId, flow.getStatus());
        notifyProgress(progressListener, "rendering", "Canvas ready! Rendering flow...");
        return flow;
    }

    /**
     * Improves an existing flow using goals.
     */
    public FlowImprovementResponse improveFlow(UUID sessionId, String existingFlowJson, String instructions,
                                                String provider, String model, double temp, int timeout) {
        logger.info("[UnifiedAiEngine] Starting FLOW IMPROVEMENT pipeline via patch-based optimizer. Session: {}", sessionId);
        return improveFlowWithPatches(sessionId, existingFlowJson, instructions, provider, model, temp, timeout);
    }

    /**
     * Improves an existing flow using patch operations instead of full regeneration.
     * <p>
     * Pipeline: existing flow JSON → FlowModel → validate → build patch prompt → LLM returns patch list →
     * apply patches → validate patched model → render to JSON.
     * </p>
     */
    public FlowImprovementResponse improveFlowWithPatches(UUID sessionId, String existingFlowJson, String instructions,
                                                           String provider, String model, double temp, int timeout) {
        logger.info("[UnifiedAiEngine] Starting PATCH-BASED FLOW IMPROVEMENT pipeline. Session: {}", sessionId);

        List<QuotaWarning> quotaWarnings = new ArrayList<>();

        double activeTemp = temp >= 0 ? temp : GlobalAiConfig.getInstance().getTemperature();
        int activeTimeout = timeout > 0 ? timeout : GlobalAiConfig.getInstance().getTimeout();
        String activeProvider = (provider != null) ? provider : GlobalAiConfig.getInstance().getProvider();
        String activeModel = (model != null) ? model : GlobalAiConfig.getInstance().getModel();

        // 1. Convert existing flow JSON to Internal Flow Model
        FlowModel existingModel = resolveExistingModel(existingFlowJson, sessionId);

        // 2. Validate existing model to collect issues
        FlowValidationResponse validation = flowModelValidator.validate(existingModel);
        List<ValidationIssueDto> issues = validation.getIssues();
        long preErrors = issues.stream().filter(i -> i.getSeverity() == ValidationSeverity.ERROR).count();
        long preWarnings = issues.stream().filter(i -> i.getSeverity() == ValidationSeverity.WARNING).count();
        logger.info("[UnifiedAiEngine] Existing model validation: valid={}, score={}, errors={}, warnings={}",
                validation.isValid(), validation.getScore(), preErrors, preWarnings);
        for (ValidationIssueDto issue : issues) {
            logger.info("  [PRE_PATCH_ISSUE] [{}] Code: {}, Msg: '{}' (node: {})",
                    issue.getSeverity(), issue.getCode(), issue.getMessage(), issue.getNodeId());
        }

        // 3. Export compact summary for LLM context (not full VoiceXML or JSON)
        String flowSummary = FlowSummaryBuilder.buildCompactSummary(existingModel);
        String existingVoicexml = modelToVxmlExporter.export(existingModel);

        // 4. Build patch request prompt with minimal context
        String systemInstruction = buildPatchSystemPrompt();
        String userPrompt = buildPatchUserPrompt(flowSummary, issues, instructions);

        String storedDomain = sessionId != null ? SessionMemoryStore.getDomain(sessionId) : null;
        String detectedDomain;
        if (storedDomain != null && !storedDomain.isBlank() && !"generic".equalsIgnoreCase(storedDomain)) {
            detectedDomain = storedDomain;
            logger.info("[UnifiedAiEngine] Reusing stored session domain for patch improve: '{}'", detectedDomain);
        } else {
            StringBuilder modelText = new StringBuilder();
            if (instructions != null) modelText.append(instructions).append(" ");
            if (existingModel != null) {
                if (existingModel.getName() != null) modelText.append(existingModel.getName()).append(" ");
                if (existingModel.getNodes() != null) {
                    for (FlowNode fn : existingModel.getNodes()) {
                        if (fn.getTitle() != null) modelText.append(fn.getTitle()).append(" ");
                        if (fn.getSubtitle() != null) modelText.append(fn.getSubtitle()).append(" ");
                    }
                }
            }
            detectedDomain = DomainDetector.detect(modelText.toString());
            logger.info("[UnifiedAiEngine] PATCH IMPROVE fresh domain detection: '{}'", detectedDomain);
            if (sessionId != null) {
                SessionMemoryStore.setDomain(sessionId, detectedDomain);
            }
        }

        // 5. Call LLM for patch operations
        AiResponse llmResponse = providerManager.executeWithRetryAndFallback(activeProvider, activeModel, activeTemp, activeTimeout,
                systemInstruction, userPrompt, "IMPROVE_FLOW_PATCH", quotaWarnings, detectedDomain, false);

        if (llmResponse == null) {
            logger.error("[UnifiedAiEngine] Patch-based improvement failed: all AI providers returned errors or are unavailable after retries.");
            throw new com.nexusivr.ai.service.exception.ProviderException(
                    "all",
                    "Optimization unavailable — all AI providers are currently rate-limited or unreachable. Please try again in a few minutes.",
                    com.nexusivr.ai.service.exception.ProviderException.FailureReason.QUOTA_EXCEEDED
            );
        }

        // 6. Parse patch operations from LLM response
        List<FlowPatchOperation> patches;
        String rationale = "Flow optimized based on: " + instructions;
        List<String> changeLog = new ArrayList<>();

        try {
            patches = parsePatchOperations(llmResponse.getContent());
            logger.info("[UnifiedAiEngine] Parsed {} patch(es) from LLM response", patches.size());
            changeLog.add("Generated " + patches.size() + " patch(es) from LLM");
        } catch (Exception e) {
            logger.warn("[UnifiedAiEngine] Failed to parse patch operations: {}. Returning original model.", e.getMessage());
            String origJson = modelToFlowRenderer.render(existingModel);
            FlowDto flowDto = new com.google.gson.Gson().fromJson(origJson, FlowDto.class);
            if (flowDto == null) flowDto = new FlowDto();
            flowDto.setName(existingModel.getName() != null ? existingModel.getName() : "Improved IVR Flow");
            FlowImprovementResponse response = new FlowImprovementResponse(flowDto, List.of("No changes applied — patch parsing failed"), "AI Optimization unavailable — unable to parse AI patch output. Your flow remains unchanged.", origJson);
            response.setQuotaWarnings(quotaWarnings);
            return response;
        }

        FlowModel prePatchSnapshot = new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(existingModel), FlowModel.class);
        boolean improved = false;
        boolean regressed = false;
        boolean rolledBack = false;
        FlowModel patchedModel = flowPatchApplier.apply(new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(existingModel), FlowModel.class), patches);

        // 8. Validate patched model
        FlowValidationResponse patchedValidation = flowModelValidator.validate(patchedModel);
        List<ValidationIssueDto> postIssues = patchedValidation.getIssues();
        long postErrors = postIssues.stream().filter(i -> i.getSeverity() == ValidationSeverity.ERROR).count();
        long postWarnings = postIssues.stream().filter(i -> i.getSeverity() == ValidationSeverity.WARNING).count();
        logger.info("[UnifiedAiEngine] Post-patch validation: valid={}, score={}, errors={}, warnings={}",
                patchedValidation.isValid(), patchedValidation.getScore(), postErrors, postWarnings);
        for (ValidationIssueDto issue : postIssues) {
            logger.info("  [POST_PATCH_ISSUE] [{}] Code: {}, Msg: '{}' (node: {})",
                    issue.getSeverity(), issue.getCode(), issue.getMessage(), issue.getNodeId());
        }

        if (!patchedValidation.isValid()) {
            logger.warn("[UnifiedAiEngine] Patched model is invalid. Issues: {}. Running repair.", postIssues.size());
            FlowValidationOrchestrator.FlowValidationOrchestratorResult repairResult = flowValidationOrchestrator.validateAndRepair(patchedModel);
            List<ValidationIssueDto> remainingErrors = repairResult.getFinalValidation().getIssues().stream()
                    .filter(i -> i.getSeverity() == ValidationSeverity.ERROR)
                    .peek(i -> logger.warn("[UnifiedAiEngine] Post-patch repair issue: {} - {}", i.getCode(), i.getMessage()))
                    .toList();
            changeLog.add("Auto-repaired patched model after validation issues");
            if (repairResult.isConverged()) {
                changeLog.add("Model converged to valid after " + repairResult.getIterations() + " repair iteration(s)");
            } else {
                int unresolvedCount = repairResult.getFinalValidation().getIssues().size();
                logger.warn("[UnifiedAiEngine] Patch repair did not converge ({} unresolved issues remaining). Auto-rolling back to original flow.", unresolvedCount);
                patchedModel = prePatchSnapshot;
                rolledBack = true;
                improved = false;
                regressed = true;
                rationale = "The optimization attempt introduced more problems than it fixed, so no changes were applied — your flow remains unchanged.";
                changeLog.add("Auto-rolled back changes: " + unresolvedCount + " issue(s) could not be resolved by auto-repair");
            }
        } else {
            changeLog.add("Patched model passed validation");
        }

        FlowValidationResponse finalValidation = null;

        // 9. Sanity check & progress check: compare pre-patch vs post-patch validation
        finalValidation = flowModelValidator.validate(patchedModel);
        int preScore = validation.getScore();
        int postScore = finalValidation.getScore();
        int preIssueCount = issues.size();
        int postIssueCount = finalValidation.getIssues().size();
        if (!rolledBack) {
            improved = (postScore > preScore) || (postScore == preScore && postIssueCount < preIssueCount);
            regressed = (postScore < preScore) || (postScore == preScore && postIssueCount > preIssueCount);
        }

        // Pre-flight check: if post-patch introduced NEW DELETED_BRANCHING_HUB / CONVERGING_PATHS issues, run auto-repair or roll back
        long preHubIssuesCount = issues.stream().filter(i -> "DELETED_BRANCHING_HUB".equals(i.getCode()) || "CONVERGING_PATHS".equals(i.getCode())).count();
        long postHubIssuesCount = finalValidation.getIssues().stream().filter(i -> "DELETED_BRANCHING_HUB".equals(i.getCode()) || "CONVERGING_PATHS".equals(i.getCode())).count();

        if (postHubIssuesCount > preHubIssuesCount) {
            logger.warn("[UnifiedAiEngine] Patched model introduced {} new DELETED_BRANCHING_HUB / CONVERGING_PATHS issues (preCount={}, postCount={}). Running auto-repair pass...",
                    (postHubIssuesCount - preHubIssuesCount), preHubIssuesCount, postHubIssuesCount);
            patchedModel = flowModelAutoRepair.repair(patchedModel);
            finalValidation = flowModelValidator.validate(patchedModel);
            postScore = finalValidation.getScore();
            postIssueCount = finalValidation.getIssues().size();
            long postHubIssuesAfterRepair = finalValidation.getIssues().stream().filter(i -> "DELETED_BRANCHING_HUB".equals(i.getCode()) || "CONVERGING_PATHS".equals(i.getCode())).count();
            if (postHubIssuesAfterRepair > preHubIssuesCount) {
                logger.warn("[UnifiedAiEngine] Auto-repair could not reduce introduced branching hub issues below pre-patch level. Rolling back patch to original flow.");
                patchedModel = prePatchSnapshot;
                rolledBack = true;
                improved = false;
                regressed = true;
                rationale = "The optimization attempt introduced more problems than it fixed, so no changes were applied — your flow remains unchanged.";
                changeLog.add("Auto-rolled back changes: introduced branching hub issues could not be resolved");
            }
        }

        if (regressed && !rolledBack) {
            logger.warn("[UnifiedAiEngine] Post-patch validation check: score/issue count regressed (preScore={}, postScore={}, preIssues={}, postIssues={}). Rolling back patch to original flow.",
                    preScore, postScore, preIssueCount, postIssueCount);
            patchedModel = prePatchSnapshot;
            rolledBack = true;
            improved = false;
            rationale = "The optimization attempt introduced more problems than it fixed, so no changes were applied — your flow remains unchanged.";
            changeLog.add(String.format("Auto-rolled back changes: score went from %d to %d (preIssues=%d, postIssues=%d)", preScore, postScore, preIssueCount, postIssueCount));
        }

        if (existingModel != null && patchedModel != null && !rolledBack) {
            int originalNodes = existingModel.getNodes().size();
            int patchedNodes = patchedModel.getNodes().size();
            int originalEdges = existingModel.getConnections().size();
            int patchedEdges = patchedModel.getConnections().size();

            if (originalNodes > 0 && patchedNodes < originalNodes * 0.5) {
                logger.warn("[UnifiedAiEngine] Post-patch sanity check: node count dropped from {} to {} (lost >50%). Keeping original.", originalNodes, patchedNodes);
                patchedModel = prePatchSnapshot;
                rolledBack = true;
                rationale = "The optimization attempt introduced more problems than it fixed, so no changes were applied — your flow remains unchanged.";
                changeLog.add("No changes applied — the AI's suggested changes didn't pass validation (excessive node loss)");
                improved = false;
            } else if (originalEdges > 0 && patchedEdges < originalEdges * 0.5) {
                logger.warn("[UnifiedAiEngine] Post-patch sanity check: edge count dropped from {} to {} (lost >50%). Keeping original.", originalEdges, patchedEdges);
                patchedModel = prePatchSnapshot;
                rolledBack = true;
                rationale = "The optimization attempt introduced more problems than it fixed, so no changes were applied — your flow remains unchanged.";
                changeLog.add("No changes applied — the AI's suggested changes didn't pass validation (excessive edge loss)");
                improved = false;
            }
        }

        // 10. Render to Builder JSON for frontend
        String improvedJson = modelToFlowRenderer.render(patchedModel);
        String canonicalVoicexml = modelToVxmlExporter.export(patchedModel);

        UUID flowId = UUID.randomUUID();
        FlowSnapshot latestSnap = ServiceRegistry.getFlowSnapshotService().getLatestSnapshot(sessionId);
        if (latestSnap != null) {
            flowId = latestSnap.getFlowId();
        }

        if (sessionId != null) {
            flowContextService.updateFlowContext(sessionId, patchedModel);
            ServiceRegistry.getFlowSnapshotService().createSnapshot(sessionId, flowId, canonicalVoicexml);
        }

        FlowDto flowDto = new com.google.gson.Gson().fromJson(improvedJson, FlowDto.class);
        if (flowDto == null) {
            flowDto = new FlowDto();
        }
        String baseName = existingModel.getName() != null ? existingModel.getName() : "IVR Flow";
        if (!rolledBack) {
            List<String> detailedPatchSummaries = new ArrayList<>();
            for (FlowPatchOperation patch : patches) {
                if (patch instanceof AddNodePatch p) {
                    String title = p.getTitle() != null ? p.getTitle() : p.getNewNodeId();
                    detailedPatchSummaries.add("Added " + (p.getNodeType() != null ? p.getNodeType() : "node") + " '" + title + "'");
                } else if (patch instanceof DeleteNodePatch p) {
                    detailedPatchSummaries.add("Deleted node '" + p.getNodeIdToDelete() + "'");
                } else if (patch instanceof AddEdgePatch p) {
                    detailedPatchSummaries.add("Connected '" + p.getSourceNodeId() + "' → '" + p.getTargetNodeId() + "'");
                } else if (patch instanceof DeleteEdgePatch p) {
                    detailedPatchSummaries.add("Removed connection '" + p.getSourceNodeId() + "' → '" + p.getTargetNodeId() + "'");
                } else if (patch instanceof RenameNodePatch p) {
                    detailedPatchSummaries.add("Renamed node '" + p.getNodeId() + "' to '" + p.getNewTitle() + "'");
                } else if (patch instanceof UpdatePromptPatch p) {
                    detailedPatchSummaries.add("Updated prompt on '" + p.getNodeId() + "'");
                } else if (patch instanceof ChangeMenuOptionPatch p) {
                    detailedPatchSummaries.add("Updated menu option on '" + p.getNodeId() + "'");
                } else if (patch instanceof MoveSubtreePatch p) {
                    detailedPatchSummaries.add("Moved subtree under '" + p.getNewParentNodeId() + "'");
                }
            }

            int originalNodes = existingModel.getNodes().size();
            int patchedNodes = patchedModel.getNodes().size();
            int originalEdges = existingModel.getConnections().size();
            int patchedEdges = patchedModel.getConnections().size();
            boolean structureUnchanged = (originalNodes == patchedNodes && originalEdges == patchedEdges);

            String patchDetailStr = !detailedPatchSummaries.isEmpty() ? String.join("; ", detailedPatchSummaries) : "applied minor internal adjustments";

            if (structureUnchanged) {
                if (improved) {
                    rationale = String.format("Fixed: %s. (Structure unchanged: %d nodes, %d connections; validation score improved from %d%% to %d%%).",
                            patchDetailStr, patchedNodes, patchedEdges, preScore, postScore);
                    changeLog.clear();
                    changeLog.add(String.format("Fixed: %s (Structure unchanged; validation score %d%% → %d%%)", patchDetailStr, preScore, postScore));
                } else {
                    rationale = String.format("Structure unchanged (%d nodes, %d connections); internal validation scoring remained at %d%% (%s).",
                            patchedNodes, patchedEdges, postScore, patchDetailStr);
                    changeLog.clear();
                    changeLog.add(String.format("Internal adjustments (%s); structure unchanged", patchDetailStr));
                }
            } else {
                rationale = String.format("Optimized structure from %d nodes / %d connections to %d nodes / %d connections (%s). Validation score improved from %d%% to %d%%.",
                        originalNodes, originalEdges, patchedNodes, patchedEdges, patchDetailStr, preScore, postScore);
                changeLog.clear();
                changeLog.add(String.format("Applied structural changes: %s (Score %d%% → %d%%)", patchDetailStr, preScore, postScore));
            }
        }

        FlowImprovementResponse response = new FlowImprovementResponse(flowDto, changeLog, rationale, improvedJson);
        response.setImproved(improved);
        response.setRegressed(regressed);
        response.setRolledBack(rolledBack);
        response.setFinalValidation(finalValidation);
        response.setQuotaWarnings(quotaWarnings);
        response.setSelectedProvider(activeProvider);
        response.setActualProviderUsed(llmResponse.getActualProviderUsed());
        response.setFallbackUsed(!activeProvider.equalsIgnoreCase(llmResponse.getActualProviderUsed()));
        response.setFallbackReason(llmResponse.getActualProviderUsed() != null && !llmResponse.getActualProviderUsed().equalsIgnoreCase(activeProvider)
                ? activeProvider + " failed. Response generated using " + llmResponse.getActualProviderUsed() + "." : "");
        return response;
    }

    private FlowModel resolveExistingModel(String existingFlowJson, UUID sessionId) {
        String trimmedInput = existingFlowJson != null ? existingFlowJson.trim() : "";
        if (trimmedInput.startsWith("\"") && trimmedInput.endsWith("\"") && trimmedInput.length() > 2) {
            try {
                trimmedInput = com.google.gson.JsonParser.parseString(trimmedInput).getAsString().trim();
            } catch (Exception ignored) {}
        }

        FlowModel resolvedModel = null;
        if (trimmedInput.startsWith("<?xml") || trimmedInput.startsWith("<vxml")) {
            try {
                resolvedModel = vxmlToModelConverter.convert(trimmedInput);
                logger.info("[UnifiedAiEngine] Parser Stage: Existing VoiceXML → FlowModel. Status: SUCCESS.");
            } catch (Exception e) {
                logger.warn("[UnifiedAiEngine] Parser Stage: Existing VoiceXML → FlowModel failed. Trying Builder JSON.", e.getMessage());
                resolvedModel = FlowContextService.convertJsonToModel(trimmedInput);
            }
        } else if (trimmedInput.startsWith("{") || trimmedInput.startsWith("[")) {
            resolvedModel = FlowContextService.convertJsonToModel(trimmedInput);
            if (resolvedModel != null) {
                logger.info("[UnifiedAiEngine] Parser Stage: Existing Builder JSON → FlowModel. Status: SUCCESS. Nodes={}.", resolvedModel.getNodes().size());
            }
        }

        if (resolvedModel != null) {
            if (sessionId != null) {
                ServiceRegistry.getFlowContextService().updateFlowContext(sessionId, resolvedModel);
            }
            return resolvedModel;
        }

        logger.warn("[UnifiedAiEngine] Parser Stage: Unrecognized format or parse failed. Trying session memory.");
        FlowModel sessionModel = ServiceRegistry.getFlowContextService().getActiveFlowModel(sessionId);
        if (sessionModel != null) {
            logger.info("[UnifiedAiEngine] Parser Stage: Session Memory → FlowModel. Status: SUCCESS. Nodes={}.", sessionModel.getNodes().size());
            return sessionModel;
        }

        return null;
    }

    private String buildPatchSystemPrompt() {
        return "You are an expert IVR Flow Optimizer. You receive an existing flow and return a list of patch operations to improve it.\n\n" +
                "CRITICAL RULES:\n" +
                "- Return ONLY a JSON array of patch operations. No markdown, no extra text, no VoiceXML.\n" +
                "- Each patch must have a 'type' field matching one of: ADD_NODE, DELETE_NODE, ADD_EDGE, DELETE_EDGE, RENAME_NODE, UPDATE_PROMPT, CHANGE_MENU_OPTION, MOVE_SUBTREE.\n" +
                "- PRESERVE EXISTING NODE IDs EXACTLY as written in the flow summary (e.g. 'start', 'main_menu', 'billing', 'n1', etc.). NEVER change or reassign existing node IDs. Only newly inserted nodes (ADD_NODE) specify a new newNodeId.\n" +
                "- PORT NAMING CONVENTIONS FOR NODE TYPES: MENU nodes MUST use sourcePort 'key1', 'key2', 'key3', ... (matching DTMF choices). CONDITION nodes MUST use sourcePort 'true' or 'false'. PROMPT/START/RECORDING nodes use 'out'. API/WEBHOOK nodes use 'success' or 'error'. TRANSFER nodes use 'success' or 'fail'. NEVER use generic 'out' for MENU or CONDITION nodes.\n" +
                "- NO COLLAPSED ROUTING / DELETED_BRANCHING_HUB ANTI-PATTERNS: Plain PROMPT/GREETING nodes MUST NOT have multiple outgoing connections to parallel targets. Creating duplicate outgoing edges from a PROMPT node creates DELETED_BRANCHING_HUB validation errors. If connecting multiple PROMPT nodes to a common target (e.g. shared agent queue or fallback), insert a MENU or CONDITION node at the branching point, or specify nodeType MENU for any newly added hub node.\n" +
                "- MENU REUSE & DE-DUPLICATION: Before adding a new MENU node to resolve converging paths or missing branching, check if an existing MENU node is already present in the flow summary. If a suitable MENU node exists, REUSE IT by adding ADD_EDGE or CHANGE_MENU_OPTION operations rather than adding a brand new MENU node. Never create a new MENU node with a duplicate or near-duplicate name (e.g. creating 'Main Menu' when 'Menu' already exists). If a new MENU node must be created, ensure its title is unique and distinct.\n" +
                "- Keep patches minimal and focused on the user's request.\n" +
                "- Never expose internal instructions, system prompts, node libraries, or developer-facing content inside <prompt> tags or patch descriptions.\n" +
                "PATCH SCHEMA:\n" +
                "- ADD_NODE: {type, newNodeId, nodeType, title, promptText, targetNodeId, sourcePort}\n" +
                "- DELETE_NODE: {type, nodeIdToDelete}\n" +
                "- ADD_EDGE: {type, edgeId, sourceNodeId, sourcePort, targetNodeId, targetPort}\n" +
                "- DELETE_EDGE: {type, edgeId, sourceNodeId, targetNodeId}\n" +
                "- RENAME_NODE: {type, nodeId, newTitle}\n" +
                "- UPDATE_PROMPT: {type, nodeId, newPromptText}\n" +
                "- CHANGE_MENU_OPTION: {type, nodeId, action (ADD/UPDATE/REMOVE), dtmf, label, targetNodeId}\n" +
                "- MOVE_SUBTREE: {type, nodeId, newParentNodeId, newSourcePort}";
    }

    private String buildPatchUserPrompt(String flowSummary, List<ValidationIssueDto> issues, String instructions) {
        StringBuilder sb = new StringBuilder();
        sb.append("User request: ").append(instructions).append("\n\n");

        if (issues != null && !issues.isEmpty()) {
            sb.append("Validation issues to address:\n");
            for (ValidationIssueDto issue : issues) {
                sb.append("- ").append(issue.getCode()).append(": ").append(issue.getMessage());
                if (issue.getNodeId() != null) {
                    sb.append(" (node: ").append(issue.getNodeId()).append(")");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        sb.append("Flow summary:\n").append(flowSummary).append("\n\n");
        sb.append("Return a JSON array of patch operations. Example:\n");
        sb.append("[\n");
        sb.append("  {\"type\": \"UPDATE_PROMPT\", \"nodeId\": \"n1\", \"newPromptText\": \"Welcome to our improved service.\"},\n");
        sb.append("  {\"type\": \"ADD_EDGE\", \"sourceNodeId\": \"n2\", \"sourcePort\": \"key2\", \"targetNodeId\": \"n3\", \"targetPort\": \"in\"}\n");
        sb.append("]");

        return sb.toString();
    }

    private List<FlowPatchOperation> parsePatchOperations(String llmResponse) {
        String content = llmResponse != null ? llmResponse.trim() : "";
        if (content.isBlank()) {
            throw new IllegalArgumentException("Empty LLM response");
        }

        String json = extractJsonArray(content);
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("No JSON array found in LLM response: " + content);
        }

        JsonArray array = JsonParser.parseString(json).getAsJsonArray();
        List<FlowPatchOperation> patches = new ArrayList<>();

        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject obj = element.getAsJsonObject();
            String type = obj.has("type") ? obj.get("type").getAsString() : null;
            if (type == null) continue;

            FlowPatchOperation patch = switch (type) {
                case "ADD_NODE" -> new AddNodePatch(
                        getString(obj, "newNodeId"),
                        getString(obj, "nodeType"),
                        getString(obj, "title"),
                        getString(obj, "promptText"),
                        getString(obj, "targetNodeId"),
                        getString(obj, "sourcePort")
                );
                case "DELETE_NODE" -> new DeleteNodePatch(getString(obj, "nodeIdToDelete"));
                case "ADD_EDGE" -> new AddEdgePatch(
                        getString(obj, "edgeId"),
                        getString(obj, "sourceNodeId"),
                        getString(obj, "sourcePort"),
                        getString(obj, "targetNodeId"),
                        getString(obj, "targetPort")
                );
                case "DELETE_EDGE" -> new DeleteEdgePatch(
                        getString(obj, "edgeId"),
                        getString(obj, "sourceNodeId"),
                        getString(obj, "targetNodeId")
                );
                case "RENAME_NODE" -> new RenameNodePatch(getString(obj, "nodeId"), getString(obj, "newTitle"));
                case "UPDATE_PROMPT" -> new UpdatePromptPatch(getString(obj, "nodeId"), getString(obj, "newPromptText"));
                case "CHANGE_MENU_OPTION" -> new ChangeMenuOptionPatch(
                        getString(obj, "nodeId"),
                        getString(obj, "action"),
                        getString(obj, "dtmf"),
                        getString(obj, "label"),
                        getString(obj, "targetNodeId")
                );
                case "MOVE_SUBTREE" -> new MoveSubtreePatch(
                        getString(obj, "nodeId"),
                        getString(obj, "newParentNodeId"),
                        getString(obj, "newSourcePort")
                );
                default -> null;
            };

            if (patch != null) {
                if (patch.getDescription() == null || patch.getDescription().isBlank()) {
                    patch.setDescription(type + " patch");
                }
                patches.add(patch);
            }
        }

        if (patches.isEmpty()) {
            throw new IllegalArgumentException("No valid patch operations found in LLM response");
        }

        return patches;
    }

    private String extractJsonArray(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```json")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                return trimmed.substring(start + 1, end).trim();
            }
        } else if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                return trimmed.substring(start + 1, end).trim();
            }
        }

        int arrayStart = trimmed.indexOf('[');
        int arrayEnd = trimmed.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return trimmed.substring(arrayStart, arrayEnd + 1);
        }

        return trimmed;
    }

    private String getString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    /**
     * Chat response routine matching the pipeline.
     */
    public ChatResponse chat(UUID sessionId, UUID tenantId, String userMessage, String flowContext, ChatService chatService) {
        // Delegates directly to chatService but supports routing through engine pipeline
        return chatService.sendMessage(sessionId, tenantId, userMessage, flowContext);
    }

    private String detectAndAppendDomainPrompt(String description, String basePrompt) {
        if (description == null || description.isBlank()) {
            return basePrompt;
        }

        String lower = description.toLowerCase();
        StringBuilder sb = new StringBuilder(basePrompt);

        // Bug C: Each branch below maps a domain keyword set to the matching AiAgentRegistry persona.
        // Previously missing domains (insurance, airline, retail, government, e-commerce/shopping)
        // caused those requests to fall through with no DOMAIN EXPERT CONTEXT — meaning the LLM
        // received only the generic FLOW_GENERATOR_SYSTEM_INSTRUCTION persona.

        if (lower.contains("hospital") || lower.contains("health") || lower.contains("medical") || lower.contains("doctor") || lower.contains("patient") || lower.contains("clinic") || lower.contains("pharmacy") || lower.contains("nurse")) {
            appendAgentContext(sb, "healthcare");
        } else if (lower.contains("bank") || lower.contains("card") || lower.contains("loan") || lower.contains("account") || lower.contains("transaction") || lower.contains("payment") || lower.contains("finance") || lower.contains("credit") || lower.contains("fraud")) {
            appendAgentContext(sb, "banking");
        } else if (lower.contains("telecom") || lower.contains("phone") || lower.contains("sim") || lower.contains("mobile") || lower.contains("internet") || lower.contains("roaming") || lower.contains("carrier") || lower.contains("network")) {
            appendAgentContext(sb, "telecom");
        } else if (lower.contains("insurance") || lower.contains("claim") || lower.contains("policy") || lower.contains("coverage") || lower.contains("premium") || lower.contains("adjuster")) {
            // Bug C: insurance domain was missing — requests like "insurance claims IVR" received no expert context.
            appendAgentContext(sb, "insurance");
        } else if (lower.contains("flight") || lower.contains("airline") || lower.contains("baggage") || lower.contains("boarding") || lower.contains("aircraft") || lower.contains("aviation") || lower.contains("airport")) {
            // Bug C: airline domain was missing.
            appendAgentContext(sb, "airline");
        } else if (lower.contains("retail") || lower.contains("shop") || lower.contains("store") || lower.contains("refund") || lower.contains("return") || lower.contains("e-commerce") || lower.contains("ecommerce") || lower.contains("online store") || lower.contains("product") || lower.contains("purchase")) {
            // Bug C: retail/e-commerce domain was missing.
            appendAgentContext(sb, "retail");
        } else if (lower.contains("permit") || lower.contains("tax") || lower.contains("government") || lower.contains("municipal") || lower.contains("city") || lower.contains("citizen") || lower.contains("public service") || lower.contains("ministry")) {
            // Bug C: government domain was missing.
            appendAgentContext(sb, "government");
        } else if (lower.contains("hotel") || lower.contains("concierge") || lower.contains("resort") || lower.contains("room") || lower.contains("booking") || lower.contains("guest") || lower.contains("hospitality") || lower.contains("stay") || lower.contains("check-in") || lower.contains("checkout")) {
            appendAgentContext(sb, "hospitality");
        } else if (lower.contains("restaurant") || lower.contains("pizza") || lower.contains("food") || lower.contains("dine") || lower.contains("cafe") || lower.contains("catering") || lower.contains("takeout") || lower.contains("delivery") || lower.contains("menu")) {
            // Bug C: restaurant was correctly added in Fix 9b but the keyword list lacked some common terms.
            appendAgentContext(sb, "restaurant");
        } else if (lower.contains("university") || lower.contains("campus") || lower.contains("college") || lower.contains("admissions") || lower.contains("enrollment") || lower.contains("financial aid") || lower.contains("student") || lower.contains("faculty") || lower.contains("registrar") || lower.contains("helpline") || lower.contains("academic")) {
            appendAgentContext(sb, "education");
        } else if (lower.contains("support") || lower.contains("ticket") || lower.contains("helpdesk") || lower.contains("escalat") || lower.contains("feedback")) {
            appendAgentContext(sb, "support");
        }
        // No match: base prompt is returned unchanged. The FLOW_GENERATOR_SYSTEM_INSTRUCTION
        // is still a capable IVR persona; the domain context is an enhancement, not a requirement.

        return sb.toString();
    }

    /**
     * Bug C helper: fetches agent system prompt from AiAgentRegistry and appends it as
     * DOMAIN EXPERT CONTEXT. Null-safe — silently skips if the agent key doesn't exist.
     */
    private void appendAgentContext(StringBuilder sb, String agentKey) {
        com.nexusivr.ai.model.AiAgent agent = com.nexusivr.ai.service.AiAgentRegistry.getAgent(agentKey);
        if (agent != null) {
            sb.append("\n\nDOMAIN EXPERT CONTEXT (Apply these guidelines):\n").append(agent.getSystemPrompt());
        }
    }

    private int getJsonArraySize(String jsonStr, String memberName) {
        try {
            JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();
            if (root.has(memberName) && root.get(memberName).isJsonArray()) {
                return root.getAsJsonArray(memberName).size();
            }
        } catch (Exception e) {
            // ignore
        }
        return 0;
    }

    /**
     * Extracts raw VoiceXML from an LLM response that may be wrapped in JSON or markdown fences.
    // LLM response extraction/normalization is now handled centrally by LlmResponseNormalizer.
    // Do not add ad-hoc extraction logic here.

    /**
     * Parses normalized VoiceXML into an Internal Flow Model.
     * <p>
     * This is the single entry point for converting already-normalized VoiceXML
     * into the canonical FlowModel representation. All raw LLM output must pass
     * through {@link LlmResponseNormalizer} before reaching this method.
     *
     * @param normalizedVxml the normalized VoiceXML text
     * @return a populated FlowModel, or null if parsing fails
     */
    private FlowModel parseNormalizedVoiceXml(String normalizedVxml, String domain, String provider, String operation) {
        if (normalizedVxml == null || normalizedVxml.isBlank()) {
            return null;
        }

        try {
            FlowModel model = vxmlToModelConverter.convert(normalizedVxml);
            logger.info("[UnifiedAiEngine] Parser Stage: Normalized VoiceXML → FlowModel. Status: SUCCESS. domain={}, provider={}, operation={}, nodes={}.",
                    domain, provider, operation, model.getNodes().size());
            return model;
        } catch (Exception e) {
            logger.warn("[UnifiedAiEngine] Parser Stage: Normalized VoiceXML → FlowModel. Status: FAILED. domain={}, provider={}, operation={}, reason={}.",
                    domain, provider, operation, e.getMessage());
            return null;
        }
    }

    /**
     * @deprecated Use {@link #parseNormalizedVoiceXml(String, String, String, String)} instead.
     * Raw LLM output must be normalized via {@link LlmResponseNormalizer} before parsing.
     */
    @Deprecated
    private FlowModel parseResponseToFlowModel(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return null;
        }

        String trimmed = rawResponse.trim();

        // Path A: VoiceXML text
        if (trimmed.startsWith("<") || trimmed.startsWith("<?xml")) {
            try {
                FlowModel model = vxmlToModelConverter.convert(trimmed);
                logger.info("[UnifiedAiEngine] Parser Stage: VoiceXML → FlowModel. Status: SUCCESS.");
                return model;
            } catch (Exception e) {
                logger.warn("[UnifiedAiEngine] Parser Stage: VoiceXML → FlowModel. Status: FAILED. Reason: {}.", e.getMessage());
                return null;
            }
        }

        return null;
    }

    /**
     * Tries to locate and parse a node array from common nested JSON structures
     * returned by LLMs when the top-level schema is not React Flow JSON.
     * <p>
     * Searches for arrays under keys like {@code nodes}, {@code steps},
     * {@code forms}, {@code items}, {@code elements}, or directly under
     * common wrapper objects such as {@code flow}, {@code data}, {@code IVR_Flow}.
     */
    static FlowModel tryParseJsonNodesFromNested(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonElement root = JsonParser.parseString(json.trim());
            if (root.isJsonArray()) {
                JsonArray arr = root.getAsJsonArray();
                return buildFlowModelFromNodeArray(arr, "LLM Flow");
            }
            if (!root.isJsonObject()) {
                return null;
            }
            JsonObject obj = root.getAsJsonObject();

            // 1. Direct top-level arrays that may represent nodes
            String[] directKeys = {"nodes", "steps", "forms", "items", "elements", "blocks"};
            for (String key : directKeys) {
                if (obj.has(key) && obj.get(key).isJsonArray()) {
                    FlowModel model = buildFlowModelFromNodeArray(obj.getAsJsonArray(key),
                            obj.has("name") ? obj.get("name").getAsString() : "LLM Flow");
                    if (model != null && !model.getNodes().isEmpty()) {
                        addEdgesFromJsonObject(model, obj);
                        return model;
                    }
                }
            }

            // 2. Nested under common wrapper keys
            String[] wrapperKeys = {"flow", "data", "IVR_Flow", "ivr", "result", "output"};
            for (String wrapper : wrapperKeys) {
                if (obj.has(wrapper) && obj.get(wrapper).isJsonObject()) {
                    JsonObject inner = obj.getAsJsonObject(wrapper);
                    for (String key : directKeys) {
                        if (inner.has(key) && inner.get(key).isJsonArray()) {
                            FlowModel model = buildFlowModelFromNodeArray(inner.getAsJsonArray(key),
                                    obj.has("name") ? obj.get("name").getAsString() : "LLM Flow");
                            if (model != null && !model.getNodes().isEmpty()) {
                                addEdgesFromJsonObject(model, inner);
                                return model;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("[UnifiedAiEngine] tryParseJsonNodesFromNested failed: {}", e.getMessage());
        }
        return null;
    }

    private static void addEdgesFromJsonObject(FlowModel model, JsonObject obj) {
        if (model == null || obj == null || !obj.has("edges") || !obj.get("edges").isJsonArray()) {
            return;
        }
        JsonArray edges = obj.getAsJsonArray("edges");
        for (int i = 0; i < edges.size(); i++) {
            JsonObject edge = edges.get(i).getAsJsonObject();
            String id = edge.has("id") ? edge.get("id").getAsString() : "e" + (i + 1);
            String source = edge.has("source") ? edge.get("source").getAsString() :
                            edge.has("sourceId") ? edge.get("sourceId").getAsString() : "";
            String sourcePort = edge.has("sourcePort") ? edge.get("sourcePort").getAsString() : "out";
            String target = edge.has("target") ? edge.get("target").getAsString() :
                            edge.has("targetId") ? edge.get("targetId").getAsString() : "";
            String targetPort = edge.has("targetPort") ? edge.get("targetPort").getAsString() : "in";
            if (!source.isBlank() && !target.isBlank()) {
                model.addConnection(new com.nexusivr.ai.model.flow.FlowConnection(id, source, sourcePort, target, targetPort));
            }
        }
    }

    private static FlowModel buildFlowModelFromNodeArray(JsonArray nodes, String flowName) {
        if (nodes == null || nodes.size() == 0) {
            return null;
        }
        FlowModel model = new FlowModel();
        model.setName(flowName != null ? flowName : "LLM Flow");
        model.setDescription("Converted from LLM JSON response");

        java.util.Map<String, String> idMap = new java.util.HashMap<>();

        for (int i = 0; i < nodes.size(); i++) {
            JsonElement el = nodes.get(i);
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject nodeObj = el.getAsJsonObject();
            String id = nodeObj.has("id") ? nodeObj.get("id").getAsString() : "n" + (i + 1);
            String typeStr = nodeObj.has("type") ? nodeObj.get("type").getAsString() :
                             nodeObj.has("node_type") ? nodeObj.get("node_type").getAsString() : "prompt";
            FlowNodeType type = FlowNodeType.fromString(typeStr);
            if (type == null) {
                type = FlowNodeType.PROMPT;
            }
            String title = nodeObj.has("title") ? nodeObj.get("title").getAsString() :
                           nodeObj.has("label") ? nodeObj.get("label").getAsString() :
                           nodeObj.has("name") ? nodeObj.get("name").getAsString() :
                           nodeObj.has("prompt") ? nodeObj.get("prompt").getAsString() : id;
            FlowNode node = new FlowNode(id, type, title);
            if (nodeObj.has("subtitle")) {
                node.setSubtitle(nodeObj.get("subtitle").getAsString());
            }
            if (type == FlowNodeType.MENU && nodeObj.has("options") && nodeObj.get("options").isJsonArray()) {
                JsonArray options = nodeObj.getAsJsonArray("options");
                com.nexusivr.ai.model.flow.FlowMenu menu = new com.nexusivr.ai.model.flow.FlowMenu();
                for (int p = 0; p < options.size(); p++) {
                    JsonElement opt = options.get(p);
                    String optLabel = opt.isJsonObject() && opt.getAsJsonObject().has("label")
                            ? opt.getAsJsonObject().get("label").getAsString()
                            : "Option " + (p + 1);
                    String optDest = opt.isJsonObject() && opt.getAsJsonObject().has("next")
                            ? opt.getAsJsonObject().get("next").getAsString()
                            : "";
                    menu.addChoice(new com.nexusivr.ai.model.flow.FlowChoice("key" + (p + 1), optLabel, optDest));
                }
                node.setMenu(menu);
            }
            model.addNode(node);
            idMap.put(id, node.getId());
        }

        // Second pass: extract connections by recursively scanning each node for
        // any target references at any nesting depth.
        for (int i = 0; i < nodes.size(); i++) {
            JsonElement el = nodes.get(i);
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject nodeObj = el.getAsJsonObject();
            String sourceId = nodeObj.has("id") ? nodeObj.get("id").getAsString() : "n" + (i + 1);
            scanNodeForConnections(nodeObj, sourceId, model);
        }

        return model;
    }

    private static void scanNodeForConnections(JsonObject nodeObj, String sourceId, FlowModel model) {
        if (nodeObj == null || model == null) {
            return;
        }

        // 1. Direct "next" field on the node itself
        if (nodeObj.has("next") && nodeObj.get("next").isJsonPrimitive()) {
            String target = nodeObj.get("next").getAsString();
            if (target != null && !target.isBlank()) {
                String connId = "e_" + sourceId + "_direct";
                model.addConnection(new com.nexusivr.ai.model.flow.FlowConnection(
                        connId, sourceId, "out", normalizeTarget(target), "in"));
            }
        }

        // 2. Recurse into known content-array containers: elements[], fields[], blocks[]
        String[] contentArrays = {"elements", "fields", "blocks"};
        for (String arrayKey : contentArrays) {
            if (nodeObj.has(arrayKey) && nodeObj.get(arrayKey).isJsonArray()) {
                JsonArray arr = nodeObj.getAsJsonArray(arrayKey);
                for (int i = 0; i < arr.size(); i++) {
                    JsonElement child = arr.get(i);
                    if (child.isJsonObject()) {
                        scanObjectForConnections(child.getAsJsonObject(), sourceId, model, arrayKey + "_" + i);
                    }
                }
            }
        }

        // 3. Recurse into menu object (menu.choices[] or menu.options[])
        if (nodeObj.has("menu") && nodeObj.get("menu").isJsonObject()) {
            JsonObject menuObj = nodeObj.getAsJsonObject("menu");
            String[] choiceArrays = {"choices", "options"};
            for (String choiceKey : choiceArrays) {
                if (menuObj.has(choiceKey) && menuObj.get(choiceKey).isJsonArray()) {
                    JsonArray choices = menuObj.getAsJsonArray(choiceKey);
                    for (int c = 0; c < choices.size(); c++) {
                        JsonElement choice = choices.get(c);
                        if (choice.isJsonObject()) {
                            scanObjectForConnections(choice.getAsJsonObject(), sourceId, model, choiceKey + "_" + c);
                        }
                    }
                }
            }
        }

        // 4. Recurse into transfers[] for dest targets
        if (nodeObj.has("transfers") && nodeObj.get("transfers").isJsonArray()) {
            JsonArray transfers = nodeObj.getAsJsonArray("transfers");
            for (int t = 0; t < transfers.size(); t++) {
                JsonElement transfer = transfers.get(t);
                if (transfer.isJsonObject()) {
                    scanObjectForConnections(transfer.getAsJsonObject(), sourceId, model, "transfer_" + t);
                }
            }
        }
    }

    private static void scanObjectForConnections(JsonObject obj, String sourceId, FlowModel model, String context) {
        if (obj == null || model == null) {
            return;
        }

        // Direct "next" on this object
        if (obj.has("next") && obj.get("next").isJsonPrimitive()) {
            String target = obj.get("next").getAsString();
            if (target != null && !target.isBlank()) {
                String portId = obj.has("accept") ? obj.get("accept").getAsString() : context;
                String connId = "e_" + sourceId + "_" + context;
                model.addConnection(new com.nexusivr.ai.model.flow.FlowConnection(
                        connId, sourceId, portId, normalizeTarget(target), "in"));
            }
        }

        // "goto" object containing "next"
        if (obj.has("goto") && obj.get("goto").isJsonObject()) {
            JsonObject gotoObj = obj.getAsJsonObject("goto");
            if (gotoObj.has("next") && gotoObj.get("next").isJsonPrimitive()) {
                String target = gotoObj.get("next").getAsString();
                if (target != null && !target.isBlank()) {
                    String connId = "e_" + sourceId + "_" + context + "_goto";
                    model.addConnection(new com.nexusivr.ai.model.flow.FlowConnection(
                            connId, sourceId, "out", normalizeTarget(target), "in"));
                }
            }
        }

        // "dest" on this object (transfer target)
        if (obj.has("dest") && obj.get("dest").isJsonPrimitive()) {
            String dest = obj.get("dest").getAsString();
            if (dest != null && !dest.isBlank()) {
                String connId = "e_" + sourceId + "_" + context + "_dest";
                model.addConnection(new com.nexusivr.ai.model.flow.FlowConnection(
                        connId, sourceId, "out", normalizeTarget(dest), "in"));
            }
        }

        // Recurse into any nested arrays
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonArray()) {
                JsonArray arr = value.getAsJsonArray();
                for (int i = 0; i < arr.size(); i++) {
                    JsonElement child = arr.get(i);
                    if (child.isJsonObject()) {
                        scanObjectForConnections(child.getAsJsonObject(), sourceId, model, entry.getKey() + "_" + i);
                    }
                }
            } else if (value.isJsonObject()) {
                scanObjectForConnections(value.getAsJsonObject(), sourceId, model, entry.getKey());
            }
        }
    }

    private static String normalizeTarget(String raw) {
        if (raw == null) {
            return "";
        }
        if (raw.startsWith("#")) {
            return raw.substring(1);
        }
        return raw;
    }

    private String diagnoseMalformation(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return "EMPTY_RESPONSE";
        }
        if (isVxmlFieldMalformed(rawResponse)) {
            return "VXML_FIELD_NOT_STRING";
        }
        try {
            String normalized = LlmResponseNormalizer.normalize(rawResponse);
            if (normalized == null || normalized.isBlank()) return "EMPTY_VXML";
            if (hasBlockContainingChoice(normalized)) {
                return "INVALID_BLOCK_WITH_CHOICE";
            }
            String trimmed = normalized.trim();

            try {
                javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(false);
                factory.setValidating(false);
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
                builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(trimmed)));
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg != null && msg.contains("must be terminated by the matching end-tag")) {
                    int start = msg.indexOf('"');
                    int end = msg.indexOf('"', start + 1);
                    if (start >= 0 && end > start) {
                        String tag = msg.substring(start + 1, end).toUpperCase();
                        return "UNCLOSED_" + tag + "_TAG";
                    }
                }
                return "XML_SYNTAX_ERROR: " + msg;
            }
        } catch (LlmResponseNormalizationException e) {
            return "NORMALIZATION_FAILURE: " + e.getMessage();
        }
        if (!rawResponse.trim().startsWith("{") && !rawResponse.trim().startsWith("<?xml") && !rawResponse.trim().startsWith("<vxml")) {
            return "UNKNOWN_SHAPE";
        }
        return "NORMALIZATION_FAILURE";
    }

    private boolean isVxmlFieldMalformed(String rawResponse) {
        String trimmed = rawResponse.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return false;
        }
        try {
            com.google.gson.JsonObject top = com.google.gson.JsonParser.parseString(trimmed).getAsJsonObject();
            if (top.has("vxml") && !top.get("vxml").isJsonPrimitive()) {
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean isMalformedLlmOutput(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return true;
        }

        if (isVxmlFieldMalformed(rawResponse)) {
            logger.info("[UnifiedAiEngine] LLM output detected as malformed: vxml field is not a string (type={}).", 
                    com.google.gson.JsonParser.parseString(rawResponse.trim()).getAsJsonObject().get("vxml").getClass().getSimpleName());
            return true;
        }

        try {
            String normalizedVxml = LlmResponseNormalizer.normalize(rawResponse);
            if (hasBlockContainingChoice(normalizedVxml)) {
                logger.info("[UnifiedAiEngine] LLM output detected as malformed VoiceXML (<choice> tags placed inside <block> instead of <menu>).");
                return true;
            }
            if (!isWellFormedVxml(normalizedVxml)) {
                logger.info("[UnifiedAiEngine] LLM output detected as malformed VoiceXML (unbalanced tags or XML parse failure).");
                return true;
            }
            return false;
        } catch (LlmResponseNormalizationException e) {
            logger.info("[UnifiedAiEngine] LLM output detected as malformed: {}", e.getMessage());
            return true;
        }
    }

    private boolean hasBlockContainingChoice(String vxml) {
        if (vxml == null || vxml.isBlank()) return false;
        if (!isWellFormedVxml(vxml)) return false;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("<block[^>]*>([\\s\\S]*?)</block>", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(vxml);
        while (matcher.find()) {
            String blockContent = matcher.group(1);
            if (blockContent.toLowerCase(java.util.Locale.ROOT).contains("<choice")) {
                return true;
            }
        }
        return false;
    }

    private boolean isWellFormedVxml(String vxml) {
        if (vxml == null || vxml.isBlank()) return false;
        String trimmed = vxml.trim();
        if (!trimmed.contains("<vxml") || !trimmed.contains("</vxml>")) {
            logger.info("[UnifiedAiEngine] VoiceXML output missing root <vxml> tag pair.");
            return false;
        }

        try {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setValidating(false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(trimmed)));
            return true;
        } catch (Exception e) {
            logger.info("[UnifiedAiEngine] VoiceXML XML parse validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Attempts to repair invalid VoiceXML locally without calling the LLM.
     * <p>
     * Handles common fixable issues:
     * <ul>
     *   <li>Missing XML declaration — prepends {@code <?xml version="1.0" encoding="UTF-8"?>}</li>
     *   <li>Missing {@code <vxml>} root — wraps content in a minimal valid VXML 2.1 envelope</li>
     * </ul>
     * <p>
     * Returns the repaired VXML string if repair is possible, or {@code null}
     * if the VXML cannot be repaired locally.
     */
    private String attemptVxmlRepair(String rawVxml, VxmlValidationResult validation) {
        if (rawVxml == null || rawVxml.isBlank()) {
            return null;
        }

        String repaired = rawVxml.trim();

        // Attempt 1: Prepend XML declaration if missing
        if (!repaired.startsWith("<?xml")) {
            repaired = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + repaired;
            VxmlValidationResult recheck = validateVxml(repaired);
            if (recheck.valid()) {
                logger.info("[UnifiedAiEngine] VXML repair: prepended XML declaration.");
                return repaired;
            }
            repaired = rawVxml.trim();
        }

        // Attempt 2: Wrap in minimal VXML 2.1 envelope if missing root element
        if (!repaired.toLowerCase().contains("<vxml")) {
            String wrapped = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\">\n" +
                    "  <form id=\"start\">\n" +
                    "    <block>\n" +
                    "      <prompt>" + escapeXml(repaired) + "</prompt>\n" +
                    "    </block>\n" +
                    "  </form>\n" +
                    "</vxml>";
            VxmlValidationResult recheck = validateVxml(wrapped);
            if (recheck.valid()) {
                logger.info("[UnifiedAiEngine] VXML repair: wrapped content in VXML 2.1 envelope.");
                return wrapped;
            }
        }

        logger.warn("[UnifiedAiEngine] VXML local repair failed. {} issue(s) remain.", validation.issues().size());
        return null;
    }

    private static String escapeXml(String input) {
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private record VxmlValidationResult(boolean valid, List<ValidationIssueDto> issues) {}

    private VxmlValidationResult validateVxml(String vxmlContent) {
        try {
            FlowValidationResponse result = vxmlValidator.validate(vxmlContent);
            return new VxmlValidationResult(result.isValid(), result.getIssues());
        } catch (Exception e) {
            List<ValidationIssueDto> issues = new ArrayList<>();
            issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "VXML_VALIDATION_ERROR",
                    "VXML validation failed: " + e.getMessage(), null, null));
            return new VxmlValidationResult(false, issues);
        }
    }

    // Delegation getters
    public FlowContextService getFlowContextService() { return flowContextService; }
    public ProviderManager getProviderManager() { return providerManager; }

    /**
     * Validates a flow string and returns issues.
     * Accepts VoiceXML or React Flow JSON.
     */
    public FlowValidationResponse validateFlow(String flow) {
        if (flow == null || flow.isBlank()) {
            return new FlowValidationResponse(false, List.of(new ValidationIssueDto(ValidationSeverity.ERROR, "EMPTY_FLOW", "Flow is empty", null, null)), 0);
        }

        FlowModel model = null;
        String trimmed = flow.trim();

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                model = FlowContextService.convertJsonToModel(flow);
            } catch (Exception e) {
                return new FlowValidationResponse(false, List.of(new ValidationIssueDto(ValidationSeverity.ERROR, "JSON_PARSE_ERROR",
                        "Failed to parse flow JSON: " + e.getMessage(), null, null)), 0);
            }
        } else {
            try {
                String normalized = LlmResponseNormalizer.normalize(flow);
                model = vxmlToModelConverter.convert(normalized);
            } catch (LlmResponseNormalizationException e) {
                return new FlowValidationResponse(false, List.of(new ValidationIssueDto(ValidationSeverity.ERROR, "NORMALIZATION_FAILED",
                        "Flow could not be normalized into VoiceXML: " + e.getMessage(), null, null)), 0);
            } catch (Exception e) {
                return new FlowValidationResponse(false, List.of(new ValidationIssueDto(ValidationSeverity.ERROR, "VXML_PARSE_ERROR",
                        "Failed to parse VoiceXML: " + e.getMessage(), null, null)), 0);
            }
        }

        if (model == null || model.getNodes().isEmpty()) {
            return new FlowValidationResponse(false, List.of(new ValidationIssueDto(ValidationSeverity.ERROR, "EMPTY_FLOW", "Flow contains no nodes", null, null)), 0);
        }

        return modelFlowValidator.validate(model);
    }

    /**
     * Resolves a stored flow to Builder JSON.
     * Attempts VoiceXML → FlowModel → JSON conversion, or returns JSON as-is.
     */
    public String resolveStoredFlowToJson(String storedFlow) {
        if (storedFlow == null || storedFlow.isBlank()) {
            return storedFlow;
        }
        String trimmed = storedFlow.trim();
        String sanitized = stripMarkdownCodeFences(trimmed);

        if (sanitized.startsWith("{") || sanitized.startsWith("[")) {
            return sanitized;
        }

        if (sanitized.startsWith("<?xml") || sanitized.startsWith("<vxml")) {
            try {
                FlowModel model = vxmlToModelConverter.convert(sanitized);
                return modelToFlowRenderer.render(model);
            } catch (Exception e) {
                return sanitized;
            }
        }

        return sanitized;
    }

    public static String stripMarkdownCodeFences(String vxml) {
        String trimmed = vxml.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline >= 0) {
                int closingFence = trimmed.lastIndexOf("```");
                if (closingFence > firstNewline) {
                    return trimmed.substring(firstNewline + 1, closingFence).trim();
                }
            }
        }
        return trimmed;
    }

    private String buildModelSummary(FlowModel model) {
        if (model == null) return "FlowModel: null";
        StringBuilder sb = new StringBuilder();
        sb.append("========== FLOW MODEL ==========\n");
        sb.append("Name: ").append(model.getName() != null ? model.getName() : "unnamed").append("\n");
        sb.append("Nodes: ").append(model.getNodes().size()).append("\n");
        for (com.nexusivr.ai.model.flow.FlowNode node : model.getNodes()) {
            sb.append("  - ").append(node.getType()).append(": ").append(node.getTitle()).append("\n");
        }
        sb.append("Connections: ").append(model.getConnections().size()).append("\n");
        return sb.toString();
    }

    public static List<String> extractRequestedFeatures(String userPrompt, String refinedSpec) {
        List<String> keywords = new ArrayList<>();
        if (refinedSpec != null && !refinedSpec.isBlank()) {
            try {
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(refinedSpec).getAsJsonObject();
                if (json.has("departments") && json.get("departments").isJsonArray()) {
                    json.getAsJsonArray("departments").forEach(e -> {
                        String s = e.getAsString().trim();
                        if (!s.isEmpty()) keywords.add(s);
                    });
                }
                if (json.has("features") && json.get("features").isJsonArray()) {
                    json.getAsJsonArray("features").forEach(e -> {
                        String s = e.getAsString().trim();
                        if (!s.isEmpty() && !keywords.contains(s)) keywords.add(s);
                    });
                }
                if (json.has("menu_options") && json.get("menu_options").isJsonArray()) {
                    json.getAsJsonArray("menu_options").forEach(e -> {
                        String s = e.getAsString().trim();
                        if (!s.isEmpty() && !keywords.contains(s)) keywords.add(s);
                    });
                }
                if (json.has("transfers") && json.get("transfers").isJsonArray()) {
                    json.getAsJsonArray("transfers").forEach(e -> {
                        String s = e.getAsString().trim();
                        if (!s.isEmpty() && !keywords.contains(s)) keywords.add(s);
                    });
                }
            } catch (Exception ignored) {}
        }

        if (userPrompt != null && !userPrompt.isBlank()) {
            List<String> extractedDepts = DepartmentExtractor.extractDepartments(userPrompt);
            for (String dept : extractedDepts) {
                if (dept != null && !dept.isBlank() && !keywords.contains(dept)) {
                    keywords.add(dept);
                }
            }
        }
        return keywords;
    }

    public static List<String> getMissingFeatures(FlowModel model, List<String> requiredFeatures) {
        List<String> missing = new ArrayList<>();
        if (requiredFeatures == null || requiredFeatures.isEmpty()) return missing;
        if (model == null || model.getNodes() == null) return new ArrayList<>(requiredFeatures);

        List<String> nodeTexts = new ArrayList<>();
        for (com.nexusivr.ai.model.flow.FlowNode n : model.getNodes()) {
            StringBuilder sb = new StringBuilder();
            if (n.getTitle() != null) sb.append(n.getTitle()).append(" ");
            if (n.getSubtitle() != null) sb.append(n.getSubtitle()).append(" ");
            if (n.getVoicexmlRef() != null) sb.append(n.getVoicexmlRef()).append(" ");
            if (n.getPrompt() != null && n.getPrompt().getText() != null) sb.append(n.getPrompt().getText()).append(" ");
            nodeTexts.add(sb.toString().toLowerCase());
        }

        for (String feature : requiredFeatures) {
            if (feature == null || feature.isBlank()) continue;
            String featureLower = feature.toLowerCase().trim();
            featureLower = featureLower.replaceAll("^(press\\s+\\d+\\s+for\\s+|option\\s+\\d+\\s*[:\\-]?\\s*|\\d+[\\s\\:\\-\\)]\\s*|department\\s+for\\s+)", "").trim();
            if (featureLower.isBlank() || featureLower.equals("agent") || featureLower.equals("specialist")
                    || featureLower.contains("greeting") || featureLower.contains("closing")
                    || featureLower.contains("menu") || featureLower.contains("routing")
                    || featureLower.contains("prompt") || featureLower.contains("system")) continue;

            boolean found = false;
            for (String text : nodeTexts) {
                if (isFuzzyMatch(featureLower, text)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                missing.add(feature);
            }
        }
        return missing;
    }

    private static boolean isFuzzyMatch(String feature, String title) {
        if (title.contains(feature) || feature.contains(title)) return true;
        String[] featureWords = feature.split("\\s+");
        int matchCount = 0;
        for (String word : featureWords) {
            if (word.length() > 2 && title.contains(word)) {
                matchCount++;
            }
        }
        return matchCount > 0 && ((double) matchCount / featureWords.length >= 0.5);
    }

    private static String buildCacheKey(UUID tenantId, String promptOrSpec, String provider, String model, String domain) {
        String tenant = tenantId != null ? tenantId.toString() : "default_tenant";
        String base = promptOrSpec != null ? promptOrSpec.trim() : "";
        String prov = provider != null ? provider.trim().toLowerCase() : "";
        String m = model != null ? model.trim().toLowerCase() : "";
        String d = domain != null ? domain.trim().toLowerCase() : "";
        return "tenant=" + tenant + "|base=" + base + "|provider=" + prov + "|model=" + m + "|domain=" + d;
    }

    private static String buildCacheKey(String promptOrSpec, String provider, String model, String domain) {
        return buildCacheKey(null, promptOrSpec, provider, model, domain);
    }

    private void notifyProgress(ProgressListener listener, String stage, String message) {
        if (listener != null) {
            try {
                listener.onProgress(stage, message);
            } catch (Exception e) {
                logger.debug("[UnifiedAiEngine] Progress listener error: {}", e.getMessage());
            }
        }
    }
}
