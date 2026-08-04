package com.nexusivr.ai.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LlmResponseNormalizerTest {

    @Test
    void testNormalizeCleanVoiceXml() {
        String input = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\"><form id=\"start\"><block><prompt>Hello.</prompt></block></form></vxml>";
        String result = LlmResponseNormalizer.normalize(input);
        assertTrue(result.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(result.contains("<vxml version=\"2.1\""));
        assertTrue(result.endsWith("</vxml>"));
    }

    @Test
    void testNormalizeMarkdownFence() {
        String input = "```xml\n<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\"><form id=\"start\"><block><prompt>Hello.</prompt></block></form></vxml>\n```";
        String result = LlmResponseNormalizer.normalize(input);
        assertFalse(result.contains("```"));
        assertTrue(result.startsWith("<?xml"));
    }

    @Test
    void testNormalizeJsonWrapper() {
        String input = "{\"vxml\":\"<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?><vxml version=\\\"2.1\\\" xmlns=\\\"http://www.w3.org/2001/vxml\\\"><form id=\\\"start\\\"><block><prompt>Hello.</prompt></block></form></vxml>\"}";
        String result = LlmResponseNormalizer.normalize(input);
        assertTrue(result.startsWith("<?xml"));
        assertTrue(result.contains("<form id=\"start\">"));
    }

    @Test
    void testNormalizeSurroundingText() {
        String input = "Here is your IVR:\n<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\"><form id=\"start\"><block><prompt>Hello.</prompt></block></form></vxml>\nLet me know if you need changes.";
        String result = LlmResponseNormalizer.normalize(input);
        assertFalse(result.contains("Here is your IVR:"));
        assertFalse(result.contains("Let me know if you need changes."));
        assertTrue(result.startsWith("<?xml"));
    }

    @Test
    void testNormalizeBom() {
        String input = "\uFEFF<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\"><form id=\"start\"><block><prompt>Hello.</prompt></block></form></vxml>";
        String result = LlmResponseNormalizer.normalize(input);
        assertFalse(result.startsWith("\uFEFF"));
        assertTrue(result.startsWith("<?xml"));
    }

    @Test
    void testNormalizeBareAmpersands() {
        String input = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\"><form id=\"start\"><block><prompt>Billing & Payments.</prompt></block></form></vxml>";
        String result = LlmResponseNormalizer.normalize(input);
        assertTrue(result.contains("Billing &amp; Payments"));
        assertFalse(result.contains("Billing & Payments"));
    }

    @Test
    void testNormalizeTruncatedRejects() {
        String input = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\"><form id=\"start\"><block><prompt>Hello.</prompt></block></form>";
        assertThrows(LlmResponseNormalizationException.class, () -> LlmResponseNormalizer.normalize(input));
    }

    @Test
    void testNormalizeErrorJsonRejects() {
        String input = "{\"error\": \"Invalid request\"}";
        assertThrows(LlmResponseNormalizationException.class, () -> LlmResponseNormalizer.normalize(input));
    }

    @Test
    void testNormalizeEmptyRejects() {
        assertThrows(LlmResponseNormalizationException.class, () -> LlmResponseNormalizer.normalize(""));
        assertThrows(LlmResponseNormalizationException.class, () -> LlmResponseNormalizer.normalize("   "));
        assertThrows(LlmResponseNormalizationException.class, () -> LlmResponseNormalizer.normalize(null));
    }

    @Test
    void testNormalizeJsonWrapperWithEscapedNewlines() {
        String raw = "{\"vxml_content\": \"<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?>\\\\n<vxml version=\\\"2.1\\\" xmlns=\\\"http://www.w3.org/2001/vxml\\\">\\\\n<form id=\\\"start\\\">\\\\n<block>\\\\n<prompt>Hello.</prompt>\\\\n</block>\\\\n</form>\\\\n</vxml>\"}";
        String result = LlmResponseNormalizer.normalize(raw);
        System.out.println("NORMALIZED RESULT:");
        System.out.println(result);
        System.out.println("---");
        assertTrue(result.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(result.contains("<vxml version=\"2.1\""));
        assertTrue(result.contains("<form id=\"start\">"));
        assertTrue(result.endsWith("</vxml>"));
    }

    @Test
    void testNormalizeStrayCharacterInProlog() {
        String raw = "{\"vxml\": \"<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?>\\`<vxml version=\\\"2.1\\\" xmlns=\\\"http://www.w3.org/2001/vxml\\\"><form id=\\\"start\\\"><block><prompt>Hello.</prompt></block></form></vxml>\"}";
        String result = LlmResponseNormalizer.normalize(raw);
        assertTrue(result.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(result.contains("<vxml version=\"2.1\""));
        assertFalse(result.contains("`"), "Stray backtick should be removed from prolog");
    }

    @Test
    void testNormalizeUnescapedCondAttributesAndPairedElseTags() {
        String raw = """
            <?xml version="1.0" encoding="UTF-8"?>
            <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
              <form id="start">
                <block>
                  <if cond="attempts < 3">
                    <prompt>Attempting retry.</prompt>
                    <goto next="#retry"/>
                  <else>
                    <prompt>Max attempts reached.</prompt>
                    <goto next="#end"/>
                  </else>
                  </if>
                </block>
              </form>
              <form id="retry">
                <block>
                  <prompt>Please try again.</prompt>
                  <goto next="#start"/>
                </block>
              </form>
              <form id="end">
                <block>
                  <prompt>Goodbye.</prompt>
                  <disconnect/>
                </block>
              </form>
            </vxml>
            """;

        String normalized = LlmResponseNormalizer.normalize(raw);
        assertTrue(normalized.contains("cond=\"attempts &lt; 3\""), "Unescaped '<' in cond attribute must be escaped to &lt;");
        assertTrue(normalized.contains("<else/>"), "Paired <else>...</else> tag must be converted to self-closing <else/>");
        assertFalse(normalized.contains("</else>"), "Closing </else> tag must be removed");

        // Verify end-to-end DOM parsing via VxmlToModelConverter
        VxmlToModelConverter converter = new VxmlToModelConverter();
        com.nexusivr.ai.model.flow.FlowModel model = assertDoesNotThrow(() -> converter.convert(normalized),
                "Normalized VXML must parse cleanly via VxmlToModelConverter");
        assertNotNull(model);
        assertTrue(model.getNodes().size() >= 3);
    }
}
