package com.nexusivr.ai.ai;

import com.nexusivr.ai.service.DomainDetector;
import com.nexusivr.ai.service.DomainFlowGenerator;
import com.nexusivr.ai.service.exception.ProviderException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TemplateGeneratorDomainTest {

    @Test
    void testGenericDomainWithoutDepartmentsGeneratesDomainAdaptiveVxml() {
        String prompt = "design a teacher assistance IVR";
        String expectedDomain = DomainDetector.detect(prompt);
        assertEquals("generic", expectedDomain, "Non-standard prompt should default to generic domain");

        TemplateGenerator generator = new TemplateGenerator(expectedDomain);
        AiResponse response = generator.generateStructuredResponse(
                "System prompt with guidelines",
                prompt,
                List.of(),
                expectedDomain
        );
        assertNotNull(response);
        assertTrue(response.isTemplateFallback());
        assertNotNull(response.getContent());
        assertTrue(response.getContent().contains("<vxml"), "Should generate valid domain-adaptive VXML");
    }

    @Test
    void testGenericDomainWithExtractedDepartmentsGeneratesVxml() {
        String prompt = "design a teacher assistance IVR with options for Schedule, Grading, and Support";
        String expectedDomain = DomainDetector.detect(prompt);

        TemplateGenerator generator = new TemplateGenerator(expectedDomain);
        AiResponse response = generator.generateStructuredResponse(
                "System prompt with guidelines",
                prompt,
                List.of(),
                expectedDomain
        );

        assertNotNull(response);
        assertTrue(response.isTemplateFallback());
        assertEquals("template-generator", response.getActualProviderUsed());

        String vxml = response.getContent();
        assertNotNull(vxml);

        // Assert exactly 1 <?xml declaration and 1 <vxml root tag
        int xmlDeclCount = countOccurrences(vxml, "<?xml");
        int vxmlTagCount = countOccurrences(vxml, "<vxml");

        assertEquals(1, xmlDeclCount, "Fallback VXML must contain exactly ONE <?xml declaration");
        assertEquals(1, vxmlTagCount, "Fallback VXML must contain exactly ONE <vxml root tag");

        // Verify custom department names are present in VXML
        assertTrue(vxml.contains("Schedule") || vxml.contains("Grading") || vxml.contains("Support"));
    }

    @Test
    void testRawVxmlInputSanitizationInPrompt() {
        String rawVxmlInput = "<?xml version=\"1.0\"?><vxml><form id=\"raw\"><prompt>Existing raw VXML document</prompt></form></vxml>";

        String escaped = DomainFlowGenerator.escape(rawVxmlInput);
        assertFalse(escaped.contains("<?xml"), "Raw XML declaration must be stripped by sanitizeDescription");
        assertFalse(escaped.contains("<vxml"), "Raw <vxml> tag must be stripped by sanitizeDescription");
        assertFalse(escaped.contains("<form"), "Raw <form> tag must be stripped by sanitizeDescription");

        TemplateGenerator generator = new TemplateGenerator("healthcare");
        AiResponse response = generator.generateStructuredResponse(
                "System instruction",
                rawVxmlInput,
                List.of(),
                "healthcare"
        );

        assertNotNull(response);
        String vxml = response.getContent();
        int xmlDeclCount = countOccurrences(vxml, "<?xml");
        int vxmlTagCount = countOccurrences(vxml, "<vxml");

        assertEquals(1, xmlDeclCount, "Fallback VXML must contain exactly ONE <?xml declaration even when prompt contains raw VXML");
        assertEquals(1, vxmlTagCount, "Fallback VXML must contain exactly ONE <vxml root tag even when prompt contains raw VXML");
    }

    @Test
    void testExplicitDomainPreservedThroughPipeline() {
        String userPrompt = "Schedule a doctor visit";
        String explicitDomain = "healthcare";

        TemplateGenerator generator = new TemplateGenerator(explicitDomain);
        AiResponse response = generator.generateStructuredResponse(
                "System prompt",
                userPrompt,
                List.of(),
                explicitDomain
        );

        assertNotNull(response);
        String vxml = response.getContent();
        assertTrue(vxml.contains("schedule an appointment") || vxml.contains("nurse") || vxml.contains("pharmacy") || vxml.contains("appointments"),
                "VXML output should reflect healthcare domain template");
    }

    @Test
    void testDomainSpecificFallbackTemplatesDistinctAndFreeOfHardwareSoftwareOptions() {
        TemplateGenerator hospitalGen = new TemplateGenerator("hospital");
        AiResponse hospitalResp = hospitalGen.generateStructuredResponse(null, "Design a hospital IVR for appointments and triage", List.of(), "hospital");
        String hospitalVxml = hospitalResp.getContent();

        assertTrue(hospitalVxml.contains("appointment") || hospitalVxml.contains("pharmacy") || hospitalVxml.contains("triage") || hospitalVxml.contains("appointments"),
                "Hospital fallback must contain medical/appointment nodes");
        assertFalse(hospitalVxml.contains("hardware") || hospitalVxml.contains("software"),
                "Hospital fallback must NOT contain IT hardware/software support options");

        TemplateGenerator restaurantGen = new TemplateGenerator("restaurant");
        AiResponse restaurantResp = restaurantGen.generateStructuredResponse(null, "Build a pizza restaurant order line", List.of(), "restaurant");
        String restaurantVxml = restaurantResp.getContent();

        assertTrue(restaurantVxml.contains("order") || restaurantVxml.contains("reservation") || restaurantVxml.contains("takeout"),
                "Restaurant fallback must contain food/order nodes");
        assertFalse(restaurantVxml.contains("hardware") || restaurantVxml.contains("software"),
                "Restaurant fallback must NOT contain IT hardware/software support options");

        TemplateGenerator hotelGen = new TemplateGenerator("hotel");
        AiResponse hotelResp = hotelGen.generateStructuredResponse(null, "Luxury hotel front desk IVR", List.of(), "hotel");
        String hotelVxml = hotelResp.getContent();

        assertTrue(hotelVxml.contains("front_desk") || hotelVxml.contains("room_service") || hotelVxml.contains("housekeeping") || hotelVxml.contains("reservations"),
                "Hotel fallback must contain hospitality/front desk nodes");
        assertFalse(hotelVxml.contains("hardware") || hotelVxml.contains("software"),
                "Hotel fallback must NOT contain IT hardware/software support options");
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
