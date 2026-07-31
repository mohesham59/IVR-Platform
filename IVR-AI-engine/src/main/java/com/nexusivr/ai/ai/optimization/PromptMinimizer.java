package com.nexusivr.ai.ai.optimization;

/**
 * Strips unnecessary content from prompts before sending to LLM.
 * <p>
 * Reduces token usage by removing redundant context,
 * compressing large payloads, and trimming verbose sections.
 * </p>
 */
public class PromptMinimizer {

    /**
     * Minimize a user prompt by removing redundant content.
     * Returns the minimized prompt.
     */
    public static String minimizeUserPrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "";
        }

        String result = prompt;

        result = removeRedundantVoiceXmlExamples(result);
        result = compressRepeatedContext(result);
        result = trimExcessiveWhitespace(result);

        return result.trim();
    }

    /**
     * Minimize a system prompt by removing verbose examples.
     */
    public static String minimizeSystemPrompt(String systemPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return "";
        }

        String result = systemPrompt;

        result = removeVerboseVoiceXmlExamples(result);
        result = compressSchemaDefinitions(result);

        return result.trim();
    }

    /**
     * Replace full VoiceXML with a compact summary.
     */
    public static String replaceVoiceXmlWithSummary(String prompt, String voiceXml) {
        if (voiceXml == null || voiceXml.isBlank()) {
            return prompt;
        }

        int lineCount = voiceXml.split("\n").length;
        if (lineCount > 50) {
            return prompt + "\n\n[VoiceXML: " + lineCount + " lines, " + voiceXml.length() + " chars - compact summary only]";
        }

        return prompt;
    }

    /**
     * Replace full React Flow JSON with a compact summary.
     */
    public static String replaceFlowJsonWithSummary(String prompt, String flowJson) {
        if (flowJson == null || flowJson.isBlank()) {
            return prompt;
        }

        return prompt + "\n\n[Flow context provided as compact summary - see FlowSummaryBuilder]";
    }

    private static String removeRedundantVoiceXmlExamples(String prompt) {
        if (prompt.contains("EXISTING VoiceXML") && prompt.contains("<vxml")) {
            int start = prompt.indexOf("EXISTING VoiceXML");
            int end = prompt.indexOf("</vxml>", start);
            if (end > start) {
                String before = prompt.substring(0, start);
                String after = prompt.substring(end + 8);
                return before + "[VoiceXML context omitted - see compact summary]" + after;
            }
        }
        return prompt;
    }

    private static String removeVerboseVoiceXmlExamples(String systemPrompt) {
        if (systemPrompt.contains("<vxml") && systemPrompt.contains("VoiceXML 2.1")) {
            return systemPrompt.replaceAll("(?s)<vxml[^>]*>.*?</vxml>", "[VoiceXML example omitted]");
        }
        return systemPrompt;
    }

    private static String compressSchemaDefinitions(String systemPrompt) {
        if (systemPrompt.contains("PATCH SCHEMA") && systemPrompt.length() > 500) {
            return systemPrompt.replaceAll("(?s)PATCH SCHEMA:.*?```json", "[Patch schema defined above]");
        }
        return systemPrompt;
    }

    private static String compressRepeatedContext(String prompt) {
        return prompt.replaceAll("(?s)(Existing.*?)(?=\\n\\n)", "$1 [truncated]");
    }

    private static String trimExcessiveWhitespace(String prompt) {
        return prompt.replaceAll("\n{3,}", "\n\n");
    }
}
