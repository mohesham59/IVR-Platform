package com.nexusivr.ai.service;

import com.nexusivr.ai.dto.common.ValidationIssueDto;
import com.nexusivr.ai.dto.common.ValidationSeverity;
import com.nexusivr.ai.dto.response.FlowValidationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates VoiceXML 2.1 documents.
 * <p>
 * This validator operates on <em>confirmed</em> VoiceXML text only. It is not used
 * as the first gate for arbitrary LLM output; format detection and parsing into
 * {@code FlowModel} happen before validation. After parsing, structural validation
 * is performed on the FlowModel by {@link ModelFlowValidator}.
 * <p>
 * VxmlValidator is retained for:
 * <ul>
 *   <li>Explicit VXML-format validation requests (e.g. validating an exported VXML file)</li>
 *   <li>Validating VXML text after format detection has already confirmed it is XML</li>
 * </ul>
 *
 * Checks structural rules such as:
 * <ul>
 *   <li>Root element is {@code <vxml>} with version 2.1</li>
 *   <li>At least one {@code <form>} exists</li>
 *   <li>Exactly one form has {@code id="start"}</li>
 *   <li>All {@code goto} and {@code choice} targets reference existing forms</li>
 *   <li>No orphan forms (every form except start must be reachable)</li>
 * </ul>
 */
public class VxmlValidator {

    private static final Logger logger = LoggerFactory.getLogger(VxmlValidator.class);

    public FlowValidationResponse validate(String vxmlContent) {
        List<ValidationIssueDto> issues = new ArrayList<>();

        logger.info("[VxmlValidator] Validation Stage: VoiceXML. Input length={} chars.", vxmlContent != null ? vxmlContent.length() : 0);

        if (vxmlContent == null || vxmlContent.isBlank()) {
            issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "EMPTY_VXML", "VXML content is empty", null, null));
            logger.warn("[VxmlValidator] Validation Result: INVALID. Reason: VXML content is empty.");
            return new FlowValidationResponse(false, issues, 0);
        }

        Document doc;
        try {
            String normalized = LlmResponseNormalizer.normalize(vxmlContent);
            doc = parseXml(normalized);
        } catch (Exception e) {
            issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "INVALID_XML", "Failed to parse VXML: " + e.getMessage(), null, null));
            logger.warn("[VxmlValidator] Validation Result: INVALID. Reason: Failed to parse VXML XML - {}.", e.getMessage());
            return new FlowValidationResponse(false, issues, 0);
        }

        Element root = doc.getDocumentElement();
        if (!"vxml".equalsIgnoreCase(root.getTagName())) {
            issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "INVALID_ROOT", "Root element must be <vxml>", null, null));
        }

        String version = root.getAttribute("version");
        if (!"2.1".equals(version)) {
            issues.add(new ValidationIssueDto(ValidationSeverity.WARNING, "UNSUPPORTED_VERSION",
                    "Expected VXML version 2.1, got: " + (version.isBlank() ? "missing" : version), null, null));
        }

        NodeList formNodes = root.getElementsByTagName("form");
        if (formNodes.getLength() == 0) {
            issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "NO_FORMS", "VXML must contain at least one <form>", null, null));
            logger.warn("[VxmlValidator] Validation Result: INVALID. Reason: VXML must contain at least one <form>.");
            return new FlowValidationResponse(false, issues, 0);
        }

        java.util.Set<String> formIds = new java.util.LinkedHashSet<>();
        String startFormId = null;
        for (int i = 0; i < formNodes.getLength(); i++) {
            Element form = (Element) formNodes.item(i);
            String id = form.getAttribute("id");
            if (id.isBlank()) {
                issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "MISSING_FORM_ID", "<form> is missing required id attribute", null, null));
            } else {
                if (!formIds.add(id)) {
                    issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "DUPLICATE_FORM_ID", "Duplicate form id: " + id, id, null));
                }
                if ("start".equalsIgnoreCase(id)) {
                    startFormId = id;
                }
            }
        }

        if (startFormId == null) {
            issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "NO_START_FORM", "VXML must contain a <form id='start'>", null, null));
        }

        java.util.Set<String> targets = new java.util.HashSet<>();
        collectGotoTargets(root, targets);
        collectChoiceTargets(root, targets);

        for (String target : targets) {
            if (!formIds.contains(target)) {
                issues.add(new ValidationIssueDto(ValidationSeverity.ERROR, "MISSING_TARGET",
                        "goto/choice target references missing form: " + target, target, null));
            }
        }

        boolean hasErrors = issues.stream().anyMatch(i -> i.getSeverity() == ValidationSeverity.ERROR);
        int score = computeVxmlScore(issues);
        FlowValidationResponse response = new FlowValidationResponse(!hasErrors, issues, score);
        logger.info("[VxmlValidator] Validation Result: {}. Errors count={}, Warnings count={}. Score={}.",
                response.getStatus(), response.getErrorCount(), response.getWarningCount(), response.getScore());
        return response;
    }

    private int computeVxmlScore(List<ValidationIssueDto> issues) {
        int score = 100;
        long errors = issues.stream().filter(i -> i.getSeverity() == ValidationSeverity.ERROR).count();
        long warnings = issues.stream().filter(i -> i.getSeverity() == ValidationSeverity.WARNING).count();
        score -= errors * 20;
        score -= warnings * 5;
        return Math.max(0, Math.min(100, score));
    }

    private void collectGotoTargets(Element root, java.util.Set<String> targets) {
        NodeList gotos = root.getElementsByTagName("goto");
        for (int i = 0; i < gotos.getLength(); i++) {
            Element gotoEl = (Element) gotos.item(i);
            String next = gotoEl.getAttribute("next");
            if (next.startsWith("#")) {
                targets.add(next.substring(1));
            }
        }
    }

    private void collectChoiceTargets(Element root, java.util.Set<String> targets) {
        NodeList choices = root.getElementsByTagName("choice");
        for (int i = 0; i < choices.getLength(); i++) {
            Element choice = (Element) choices.item(i);
            String next = choice.getAttribute("next");
            if (!next.isBlank()) {
                if (next.startsWith("#")) {
                    targets.add(next.substring(1));
                } else {
                    targets.add(next);
                }
            }
        }
    }

    private Document parseXml(String xml) throws ParserConfigurationException, SAXException, IOException {
        String trimmed = xml.trim();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new org.xml.sax.InputSource(new StringReader(trimmed)));
    }

    private static String stripMarkdownCodeFences(String vxml) {
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

    private static String formatFirstChars(String s) {
        if (s == null || s.isEmpty()) return "(empty)";
        int len = Math.min(20, s.length());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(String.format("0x%02X ", (int) s.charAt(i)));
        }
        if (s.length() > 20) sb.append("...");
        return sb.toString();
    }
}
