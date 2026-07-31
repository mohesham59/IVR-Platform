package com.nexusivr.ai.ai;

import com.nexusivr.ai.config.LlmConfig;
import com.nexusivr.ai.model.Message;
import com.nexusivr.ai.service.DomainDetector;
import com.nexusivr.ai.service.DomainFlowGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Built-in fallback provider used only when every configured AI provider
 * (Groq, Gemini, …) is unavailable or rate-limited.
 * <p>
 * Returns deterministic, domain-specific VoiceXML flows so the caller never
 * receives a hard failure. This provider is appended last in the
 * {@link ProviderManager} priority chain.
 */
public class TemplateGenerator implements LlmClient {

    private static final Logger logger = LoggerFactory.getLogger(TemplateGenerator.class);

    private final String model;
    private final String domain;
    private final DomainFlowGenerator domainFlowGenerator;

    public TemplateGenerator() {
        this(LlmConfig.getGroqModel(), null);
    }

    public TemplateGenerator(String domain) {
        this(LlmConfig.getGroqModel(), domain);
    }

    public TemplateGenerator(String model, String domain) {
        this(model, domain, new DomainFlowGenerator());
    }

    public TemplateGenerator(String model, String domain, DomainFlowGenerator domainFlowGenerator) {
        this.model = model != null && !model.isBlank() ? model : "template-generator";
        this.domain = domain;
        this.domainFlowGenerator = domainFlowGenerator != null ? domainFlowGenerator : new DomainFlowGenerator();
    }

    @Override
    public String getProviderName() {
        return "template-generator";
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public AiResponse generateResponse(String prompt, List<Message> history) {
        return generateResponse(null, prompt, history);
    }

    @Override
    public AiResponse generateResponse(String systemPrompt, String userPrompt, List<Message> history) {
        String content = "[Template Generator] All AI providers are temporarily unavailable. " +
                "Please retry your request shortly or contact support if the issue persists.";
        logger.warn("[TemplateGenerator] Returning template response. Prompt length={}", userPrompt != null ? userPrompt.length() : 0);
        return new AiResponse(content, model, 0, 0, false);
    }

    @Override
    public AiResponse generateStructuredResponse(String prompt, List<Message> history) {
        return generateStructuredResponse(null, prompt, history);
    }

    @Override
    public AiResponse generateStructuredResponse(String systemPrompt, String userPrompt, List<Message> history) {
        return generateStructuredResponse(systemPrompt, userPrompt, history, null);
    }

    public AiResponse generateStructuredResponse(String systemPrompt, String userPrompt, List<Message> history, String explicitDomain) {
        String activeDomain = explicitDomain;
        if (activeDomain == null || activeDomain.isBlank()) {
            activeDomain = this.domain;
        }
        if (activeDomain == null || activeDomain.isBlank()) {
            activeDomain = DomainDetector.detect(userPrompt);
        }
        String vxml = domainFlowGenerator.generateVxml(activeDomain, userPrompt);
        validateVxmlStructure(vxml);
        logger.warn("[TemplateGenerator] Returning domain-specific fallback for domain='{}'. Prompt length={}", activeDomain, userPrompt != null ? userPrompt.length() : 0);
        return new AiResponse(vxml, model, 0, 0, false, true, "TemplateGenerator", null, 200, null, "template-generator");
    }

    private void validateVxmlStructure(String vxml) {
        if (vxml == null || vxml.isBlank()) {
            throw new IllegalStateException("TemplateGenerator fallback returned empty or null VoiceXML string");
        }
        int xmlDeclCount = countOccurrences(vxml, "<?xml");
        int vxmlTagCount = countOccurrences(vxml, "<vxml");

        if (xmlDeclCount != 1 || vxmlTagCount != 1) {
            logger.error("[TemplateGenerator] Malformed VoiceXML structure detected! xmlDeclCount={}, vxmlTagCount={}",
                    xmlDeclCount, vxmlTagCount);
            throw new IllegalStateException("Malformed VoiceXML generated in TemplateGenerator fallback: " +
                    "xmlDeclCount=" + xmlDeclCount + ", vxmlTagCount=" + vxmlTagCount);
        }
    }

    private int countOccurrences(String text, String target) {
        if (text == null || target == null || target.isEmpty()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }
}
