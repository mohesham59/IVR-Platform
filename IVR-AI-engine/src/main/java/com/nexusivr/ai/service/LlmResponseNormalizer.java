package com.nexusivr.ai.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Locale;
import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Single normalization layer for all LLM responses.
 * <p>
 * Before any VoiceXML parsing occurs, raw LLM output must pass through
 * this class. It handles every known LLM formatting anomaly and either
 * returns clean VoiceXML or throws a descriptive {@link LlmResponseNormalizationException}.
 * </p>
 */
public class LlmResponseNormalizer {

    private static final Logger logger = LoggerFactory.getLogger(LlmResponseNormalizer.class);

    // Matches UTF-8 BOM (EF BB BF) at start of string
    private static final Pattern BOM_PATTERN = Pattern.compile("^\uFEFF");
    // Matches XML declaration possibly preceded by whitespace/newlines
    private static final Pattern XML_DECL_PATTERN = Pattern.compile("(?i)<\\?xml\\s+version=\"1\\.0\"\\s+encoding=\"UTF-8\"\\?>");
    // Matches opening <vxml tag
    private static final Pattern VXML_OPEN_PATTERN = Pattern.compile("(?i)<vxml\\s+version=\"2\\.1\"");
    // Matches closing </vxml>
    private static final Pattern VXML_CLOSE_PATTERN = Pattern.compile("(?i)</vxml>");
    // Matches markdown code fence start
    private static final Pattern FENCE_START_PATTERN = Pattern.compile("```(?:xml)?\\s*\\n?");
    // Matches markdown code fence end
    private static final Pattern FENCE_END_PATTERN = Pattern.compile("\\n?```");

    private LlmResponseNormalizer() {}

    /**
     * Normalizes raw LLM output into clean VoiceXML text.
     * <p>
     * This is the ONLY entry point for LLM response preprocessing.
     * All callers (UnifiedAiEngine, tests, future pipelines) must use this method.
     *
     * @param rawResponse the raw text returned by the LLM
     * @return clean VoiceXML text ready for parsing
     * @throws LlmResponseNormalizationException if the response cannot be normalized
     */
    public static String normalize(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new LlmResponseNormalizationException("LLM returned empty response");
        }

        String text = rawResponse;
        logger.info("[LlmResponseNormalizer] Input length={} chars, first 20 chars (codes): {}", text.length(), formatFirstChars(text));

        // Step 1: Remove UTF-8 BOM
        text = removeBom(text);

        // Step 2: Unescape JSON-string-embedded content (\" → ", \\n → newline, etc.)
        text = unescapeJsonStringContent(text);

        // Step 3: Try to extract from JSON wrapper first
        text = extractFromJsonWrapper(text);

        // Step 4: Extract from markdown code fences
        text = extractFromMarkdownFences(text);

        // Step 5: Extract XML from surrounding prose
        text = extractFromSurroundingText(text);

        // Step 6: Final cleanup
        text = finalCleanup(text);

        // Step 7: Validate we have recognizable VoiceXML
        validateVoiceXmlStructure(text);

        logger.info("[LlmResponseNormalizer] Normalized output length={} chars, first 20 chars (codes): {}", text.length(), formatFirstChars(text));
        return text;
    }

    private static String removeBom(String text) {
        String trimmed = BOM_PATTERN.matcher(text).replaceFirst("");
        if (!trimmed.equals(text)) {
            logger.info("[LlmResponseNormalizer] Removed UTF-8 BOM.");
        }
        return trimmed;
    }

    private static String unescapeJsonStringContent(String input) {
        if (input == null || input.isBlank()) return input;
        String result = input;
        result = result.replace("\\\"", "\"");
        result = result.replace("\\\\n", "\n");
        result = result.replace("\\\\t", "\t");
        result = result.replace("\\\\\\\\", "\\");
        return result;
    }

    private static String extractFromJsonWrapper(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return text;
        }

        try {
            JsonObject top = JsonParser.parseString(trimmed).getAsJsonObject();

            // Case A: {"vxml":"<?xml ..."}
            if (top.has("vxml") && top.get("vxml").isJsonPrimitive()) {
                String inner = top.get("vxml").getAsString();
                if (inner != null && !inner.isBlank()) {
                    logger.info("[LlmResponseNormalizer] Extracted VXML from JSON wrapper key 'vxml'.");
                    return sanitizeProlog(inner.trim());
                }
            }

            // Case A2: {"vxml_content":"<?xml ..."} or other common VXML keys
            String[] vxmlKeys = {"vxml_content", "vxmlCode", "voicexml", "xml", "content"};
            for (String key : vxmlKeys) {
                if (top.has(key) && top.get(key).isJsonPrimitive()) {
                    String inner = top.get(key).getAsString();
                    if (inner != null && !inner.isBlank()) {
                        logger.info("[LlmResponseNormalizer] Extracted VXML from JSON wrapper key '{}'.", key);
                        return sanitizeProlog(inner.trim());
                    }
                }
            }

            // Case B: {"error":"..."} or {"message":"..."} - not VoiceXML
            if (top.keySet().size() == 1 && (top.has("error") || top.has("message"))) {
                throw new LlmResponseNormalizationException(
                        "LLM returned error-shaped JSON instead of VoiceXML: " + trimmed.substring(0, Math.min(200, trimmed.length()))
                );
            }

            // Case C: JSON flow description - convert to VoiceXML
            if (top.has("nodes") || top.has("forms") || top.has("IVR_Flow") || top.has("flow")) {
                if (isJsonFlowEmpty(top)) {
                    throw new LlmResponseNormalizationException(
                            "LLM returned JSON flow description with empty content: " + trimmed.substring(0, Math.min(200, trimmed.length()))
                    );
                }
                logger.warn("[LlmResponseNormalizer] LLM returned JSON flow description instead of VoiceXML. Converting.");
                return convertJsonFlowToVxml(trimmed);
            }
        } catch (com.google.gson.JsonSyntaxException ignored) {
            // Not valid JSON - fall through to other extraction methods
        } catch (LlmResponseNormalizationException e) {
            throw e;
        } catch (Exception ignored) {
            // Other parse errors - fall through
        }

        return text;
    }

    private static boolean isJsonFlowEmpty(JsonObject top) {
        if (top.has("nodes") && top.get("nodes").isJsonArray() && top.getAsJsonArray("nodes").size() == 0) {
            return true;
        }
        if (top.has("forms") && top.get("forms").isJsonArray() && top.getAsJsonArray("forms").size() == 0) {
            return true;
        }
        if (top.has("flow") && top.get("flow").isJsonObject()) {
            JsonObject flow = top.getAsJsonObject("flow");
            if (flow.has("nodes") && flow.get("nodes").isJsonArray() && flow.getAsJsonArray("nodes").size() == 0) {
                return true;
            }
        }
        return false;
    }

    private static String extractFromMarkdownFences(String text) {
        String trimmed = text.trim();
        if (!trimmed.contains("```")) {
            return text;
        }

        int firstFence = trimmed.indexOf("```");
        int newlineAfterFence = trimmed.indexOf('\n', firstFence);
        if (newlineAfterFence < 0) {
            return text;
        }

        int lastFence = trimmed.lastIndexOf("```", newlineAfterFence);
        if (lastFence <= newlineAfterFence) {
            return text;
        }

        String inner = trimmed.substring(newlineAfterFence + 1, lastFence).trim();
        if (inner.contains("<vxml") || inner.contains("<?xml")) {
            logger.info("[LlmResponseNormalizer] Extracted VXML from markdown code fence.");
            return inner;
        }

        return text;
    }

    private static String extractFromSurroundingText(String text) {
        String trimmed = text.trim();

        // Already starts with XML declaration or vxml tag
        if (trimmed.startsWith("<?xml") || trimmed.startsWith("<vxml")) {
            return text;
        }

        // Try to find XML declaration
        int xmlStart = trimmed.indexOf("<?xml");
        if (xmlStart < 0) {
            xmlStart = trimmed.indexOf("<vxml");
        }
        if (xmlStart < 0) {
            return text;
        }

        // Find closing </vxml>
        int xmlEnd = trimmed.lastIndexOf("</vxml>");
        if (xmlEnd >= 0 && xmlEnd > xmlStart) {
            String extracted = trimmed.substring(xmlStart, xmlEnd + "</vxml>".length());
            logger.info("[LlmResponseNormalizer] Extracted VXML from surrounding text (positions {} to {}).", xmlStart, xmlEnd);
            return extracted;
        }

        return text;
    }

    private static String finalCleanup(String text) {
        String result = text.trim();

        // Remove leading whitespace/newlines before XML declaration
        if (!result.startsWith("<?xml") && !result.startsWith("<vxml")) {
            int xmlStart = result.indexOf("<?xml");
            if (xmlStart < 0) xmlStart = result.indexOf("<vxml");
            if (xmlStart > 0) {
                result = result.substring(xmlStart);
            }
        }

        // Ensure XML declaration is on its own line or at start
        result = result.replaceFirst("(?i)^\\s*<\\?xml\\s+version=\"1\\.0\"\\s+encoding=\"UTF-8\"\\?>\\s*", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");

        // Sanitize prolog: remove any stray non-whitespace characters between XML declaration and <vxml>
        result = sanitizeProlog(result);

        // Sanitize bare ampersands
        result = sanitizeBareAmpersands(result);

        return result;
    }

    private static void validateVoiceXmlStructure(String text) {
        String trimmed = text.trim();

        if (!trimmed.startsWith("<?xml") && !trimmed.startsWith("<vxml")) {
            throw new LlmResponseNormalizationException(
                    "Normalized output does not start with XML declaration or <vxml> tag. First chars: " + formatFirstChars(trimmed)
            );
        }

        if (!VXML_OPEN_PATTERN.matcher(trimmed).find()) {
            throw new LlmResponseNormalizationException(
                    "Normalized output is missing <vxml version=\"2.1\" ...> root element."
            );
        }

        if (!VXML_CLOSE_PATTERN.matcher(trimmed).find()) {
            throw new LlmResponseNormalizationException(
                    "Normalized output is truncated: missing closing </vxml> tag."
            );
        }
    }

    static String sanitizeBareAmpersands(String xml) {
        if (xml == null || !xml.contains("&")) {
            return xml;
        }
        return xml.replaceAll("&(?!amp;|lt;|gt;|apos;|quot;|#\\d+;|#x[0-9a-fA-F]+;)", "&amp;");
    }

    private static String sanitizeProlog(String vxml) {
        if (vxml == null || vxml.isBlank()) return vxml;
        String result = vxml;
        int xmlDeclEnd = result.indexOf("?>");
        int vxmlStart = result.indexOf("<vxml");
        if (xmlDeclEnd >= 0 && vxmlStart > xmlDeclEnd) {
            String between = result.substring(xmlDeclEnd + 2, vxmlStart);
            String cleaned = between.replaceAll("[^\\s]", "");
            if (!cleaned.equals(between)) {
                result = result.substring(0, xmlDeclEnd + 2) + cleaned + result.substring(vxmlStart);
                logger.info("[LlmResponseNormalizer] Sanitized prolog: removed {} non-whitespace character(s) between XML declaration and <vxml>.", between.length() - cleaned.length());
            }
        }
        return result.trim();
    }

    private record EdgeInfo(String sourcePort, String target) {}

    private static String convertJsonFlowToVxml(String jsonFlow) {
        try {
            JsonObject json = JsonParser.parseString(jsonFlow).getAsJsonObject();
            String flowName = json.has("name") ? json.get("name").getAsString() : "IVR Flow";

            StringBuilder vxml = new StringBuilder();
            vxml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            vxml.append("<vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\">\n");

            java.util.Map<String, java.util.List<EdgeInfo>> edgesBySource = new java.util.LinkedHashMap<>();
            java.util.Set<String> allNodeIds = new java.util.LinkedHashSet<>();

            if (json.has("nodes") && json.get("nodes").isJsonArray()) {
                json.getAsJsonArray("nodes").forEach(node -> {
                    if (node.isJsonObject()) {
                        JsonObject obj = node.getAsJsonObject();
                        String nodeId = obj.has("id") ? obj.get("id").getAsString() : null;
                        if (nodeId != null && !nodeId.isBlank()) {
                            allNodeIds.add(nodeId);
                        }
                    }
                });
            }

            if (json.has("edges") && json.get("edges").isJsonArray()) {
                json.getAsJsonArray("edges").forEach(edge -> {
                    if (edge.isJsonObject()) {
                        JsonObject obj = edge.getAsJsonObject();
                        String source = obj.has("source") ? obj.get("source").getAsString() :
                                        obj.has("sourceId") ? obj.get("sourceId").getAsString() : null;
                        String sourcePort = obj.has("sourcePort") ? obj.get("sourcePort").getAsString() : "out";
                        String target = obj.has("target") ? obj.get("target").getAsString() :
                                        obj.has("targetId") ? obj.get("targetId").getAsString() : null;
                        if (source != null && target != null && !source.isBlank() && !target.isBlank()) {
                            edgesBySource.computeIfAbsent(source, k -> new java.util.ArrayList<>())
                                    .add(new EdgeInfo(sourcePort, target));
                        }
                    }
                });
            }

            java.util.Map<String, JsonObject> nodeById = new java.util.LinkedHashMap<>();
            if (json.has("nodes") && json.get("nodes").isJsonArray()) {
                json.getAsJsonArray("nodes").forEach(node -> {
                    if (node.isJsonObject()) {
                        JsonObject obj = node.getAsJsonObject();
                        String nodeId = obj.has("id") ? obj.get("id").getAsString() : null;
                        if (nodeId != null && !nodeId.isBlank()) {
                            nodeById.put(nodeId, obj);
                        }
                    }
                });
            }

            java.util.List<String> droppedEdges = new java.util.ArrayList<>();

            for (String nodeId : allNodeIds) {
                JsonObject nodeObj = nodeById.get(nodeId);
                String nodeType = nodeObj != null && nodeObj.has("type") ? nodeObj.get("type").getAsString() : "prompt";
                String title = nodeObj != null ? (nodeObj.has("title") ? nodeObj.get("title").getAsString() :
                                  nodeObj.has("label") ? nodeObj.get("label").getAsString() : nodeId) : nodeId;

                java.util.List<EdgeInfo> outgoing = edgesBySource.getOrDefault(nodeId, java.util.List.of());

                switch (nodeType.toLowerCase(Locale.ROOT)) {
                    case "start" -> {
                        vxml.append("  <form id=\"").append(escapeXml(nodeId)).append("\">\n");
                        vxml.append("    <block>\n");
                        vxml.append("      <prompt>Welcome to ").append(escapeXml(flowName)).append(".</prompt>\n");
                        if (!outgoing.isEmpty()) {
                            vxml.append("      <goto next=\"#").append(escapeXml(outgoing.get(0).target)).append("\"/>\n");
                        }
                        vxml.append("    </block>\n");
                        vxml.append("  </form>\n");
                    }
                    case "dtmf_menu", "menu" -> {
                        vxml.append("  <form id=\"").append(escapeXml(nodeId)).append("\">\n");
                        vxml.append("    <menu>\n");
                        vxml.append("      <prompt>").append(escapeXml(title)).append("</prompt>\n");
                        if (outgoing.isEmpty()) {
                            droppedEdges.add("Menu node '" + nodeId + "' has no outgoing choices");
                        }
                        for (EdgeInfo edge : outgoing) {
                            String dtmf = portToDtmf(edge.sourcePort);
                            vxml.append("      <choice accept=\"digits ").append(escapeXml(dtmf))
                                    .append("\" next=\"#").append(escapeXml(edge.target)).append("\">")
                                    .append(escapeXml(title)).append("</choice>\n");
                        }
                        vxml.append("    </menu>\n");
                        vxml.append("  </form>\n");
                    }
                    case "dtmf_input", "input" -> {
                        vxml.append("  <form id=\"").append(escapeXml(nodeId)).append("\">\n");
                        vxml.append("    <field name=\"").append(escapeXml(nodeId)).append("\" type=\"1\">\n");
                        vxml.append("      <prompt>").append(escapeXml(title)).append("</prompt>\n");
                        vxml.append("      <grammar mode=\"dtmf\" version=\"1.0\">\n");
                        vxml.append("        <rule id=\"digits\"><one-of>\n");
                        for (int d = 0; d <= 9; d++) {
                            vxml.append("          <item>").append(d).append("</item>\n");
                        }
                        vxml.append("        </one-of></rule>\n");
                        vxml.append("      </grammar>\n");
                        for (EdgeInfo edge : outgoing) {
                            String handler = switch (edge.sourcePort) {
                                case "timeout" -> "noinput";
                                case "error", "invalid" -> "nomatch";
                                default -> "filled";
                            };
                            vxml.append("      <").append(handler).append(">\n");
                            vxml.append("        <goto next=\"#").append(escapeXml(edge.target)).append("\"/>\n");
                            vxml.append("      </").append(handler).append(">\n");
                        }
                        vxml.append("    </field>\n");
                        vxml.append("  </form>\n");
                    }
                    case "condition" -> {
                        vxml.append("  <form id=\"").append(escapeXml(nodeId)).append("\">\n");
                        vxml.append("    <block>\n");
                        vxml.append("      <if cond=\"true\">\n");
                        for (EdgeInfo edge : outgoing) {
                            if ("true".equalsIgnoreCase(edge.sourcePort)) {
                                vxml.append("        <goto next=\"#").append(escapeXml(edge.target)).append("\"/>\n");
                                break;
                            }
                        }
                        vxml.append("      </if>\n");
                        for (EdgeInfo edge : outgoing) {
                            if (!"true".equalsIgnoreCase(edge.sourcePort) && !"false".equalsIgnoreCase(edge.sourcePort)) {
                                vxml.append("      <elseif cond=\"true\">\n");
                                vxml.append("        <goto next=\"#").append(escapeXml(edge.target)).append("\"/>\n");
                                vxml.append("      </elseif>\n");
                            }
                        }
                        boolean hasFalse = false;
                        for (EdgeInfo edge : outgoing) {
                            if ("false".equalsIgnoreCase(edge.sourcePort)) {
                                hasFalse = true;
                                break;
                            }
                        }
                        if (hasFalse) {
                            vxml.append("      <else>\n");
                            for (EdgeInfo edge : outgoing) {
                                if ("false".equalsIgnoreCase(edge.sourcePort)) {
                                    vxml.append("        <goto next=\"#").append(escapeXml(edge.target)).append("\"/>\n");
                                }
                            }
                            vxml.append("      </else>\n");
                        }
                        vxml.append("    </block>\n");
                        vxml.append("  </form>\n");
                    }
                    case "transfer" -> {
                        vxml.append("  <form id=\"").append(escapeXml(nodeId)).append("\">\n");
                        vxml.append("    <block>\n");
                        vxml.append("      <prompt>").append(escapeXml(title)).append("</prompt>\n");
                        if (!outgoing.isEmpty()) {
                            vxml.append("      <transfer dest=\"#").append(escapeXml(outgoing.get(0).target)).append("\"/>\n");
                        } else {
                            vxml.append("      <transfer dest=\"+1000\"/>\n");
                        }
                        vxml.append("    </block>\n");
                        vxml.append("  </form>\n");
                    }
                    case "end" -> {
                        vxml.append("  <form id=\"").append(escapeXml(nodeId)).append("\">\n");
                        vxml.append("    <block>\n");
                        vxml.append("      <prompt>").append(escapeXml(title)).append("</prompt>\n");
                        vxml.append("      <disconnect/>\n");
                        vxml.append("    </block>\n");
                        vxml.append("  </form>\n");
                    }
                    default -> {
                        vxml.append("  <form id=\"").append(escapeXml(nodeId)).append("\">\n");
                        vxml.append("    <block>\n");
                        vxml.append("      <prompt>").append(escapeXml(title)).append("</prompt>\n");
                        if (!outgoing.isEmpty()) {
                            vxml.append("      <goto next=\"#").append(escapeXml(outgoing.get(0).target)).append("\"/>\n");
                        } else {
                            droppedEdges.add("Node '" + nodeId + "' has no outgoing transition");
                        }
                        vxml.append("    </block>\n");
                        vxml.append("  </form>\n");
                    }
                }
            }

            vxml.append("</vxml>");

            if (!droppedEdges.isEmpty()) {
                logger.warn("[LlmResponseNormalizer] JSON-to-VXML conversion dropped {} transitions: {}", droppedEdges.size(), droppedEdges);
            }

            logger.info("[LlmResponseNormalizer] Converted JSON flow to VoiceXML. Nodes={}, Edges={}, Dropped={}.",
                    allNodeIds.size(), edgesBySource.values().stream().mapToInt(java.util.List::size).sum(), droppedEdges.size());
            return vxml.toString();
        } catch (Exception e) {
            throw new LlmResponseNormalizationException("Failed to convert JSON flow to VoiceXML: " + e.getMessage(), e);
        }
    }

    private static String portToDtmf(String sourcePort) {
        if (sourcePort == null || sourcePort.isBlank()) {
            return "1";
        }
        String port = sourcePort.trim();
        if (port.startsWith("key")) {
            return port.substring(3);
        }
        if (port.matches("\\d+")) {
            return port;
        }
        return "1";
    }

    private static String escapeXml(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> sb.append("&");
                case '<' -> sb.append("<");
                case '>' -> sb.append(">");
                case '"' -> sb.append("\"");
                case '\'' -> sb.append("'");
                default -> sb.append(c);
            }
        }
        return sb.toString();
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
