package com.nexusivr.ai.service;

import com.nexusivr.ai.model.flow.FlowConnection;
import com.nexusivr.ai.model.flow.FlowNode;
import com.nexusivr.ai.model.flow.FlowNodeType;
import com.nexusivr.ai.model.flow.FlowModel;
import com.nexusivr.ai.model.flow.FlowPrompt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FlowBusinessNameResolutionTest {

    @TempDir
    Path tempDraftsDir;

    @TempDir
    Path tempScenariosDir;

    private FlowPublishService publishService;
    private ModelToVxmlExporter exporter;
    private VxmlToModelConverter converter;

    @BeforeEach
    void setUp() {
        exporter = new ModelToVxmlExporter();
        converter = new VxmlToModelConverter();
        publishService = new FlowPublishService(exporter, new FlowModelValidator(), tempScenariosDir.toString());
    }

    @Test
    @DisplayName("VxmlToModelConverter does not populate model.name with spoken prompt greeting text")
    void testConverterDoesNotSetModelNameFromGreetingPrompt() throws Exception {
        String greetingText = "Welcome to Hospitality Services. Please listen carefully to all choices as our menu options have changed.";
        String vxml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
              <form id="start">
                <block>
                  <prompt>%s</prompt>
                  <goto next="#end"/>
                </block>
              </form>
              <form id="end">
                <block><prompt>Goodbye.</prompt><disconnect/></block>
              </form>
            </vxml>
            """.formatted(greetingText);

        FlowModel model = converter.convert(vxml);
        assertNotNull(model, "Model must parse successfully");
        assertNull(model.getName(), "VxmlToModelConverter must NOT set model.name to spoken greeting prompt text!");
    }

    @Test
    @DisplayName("FlowDraftService.isSpokenGreeting identifies conversational audio text and rejects it")
    void testIsSpokenGreetingIdentifiesPromptText() {
        assertTrue(FlowDraftService.isSpokenGreeting("Welcome to Hospitality Services. Press 1 for Reservations."));
        assertTrue(FlowDraftService.isSpokenGreeting("Thank you for calling Meridian Health Clinic. Please listen carefully."));
        assertTrue(FlowDraftService.isSpokenGreeting("Please hold while we transfer your call to an agent."));
        assertTrue(FlowDraftService.isSpokenGreeting("Hello, thank you for contacting our customer service department today."));

        assertFalse(FlowDraftService.isSpokenGreeting("Hospitality Services"));
        assertFalse(FlowDraftService.isSpokenGreeting("Meridian Health Clinic"));
        assertFalse(FlowDraftService.isSpokenGreeting("Pizza Place IVR"));
        assertFalse(FlowDraftService.isSpokenGreeting("hospitality_ivr"));
    }

    @Test
    @DisplayName("Save draft and Publish VXML filenames never contain spoken greeting text substrings")
    void testSaveAndPublishFilenamesNeverContainGreetingSubstrings() throws IOException {
        String tenantId = "00000000_0000_0000_0000_000000000001";
        String flowId = "flow_hotel_123";
        String longGreeting = "Welcome to Hospitality Services. Please listen carefully as our menu options have changed.";

        // 1. Assert getBaseName ignores long spoken greeting and falls back to clean base name
        String resolvedBaseName = FlowDraftService.getBaseName(tenantId, flowId, longGreeting);
        assertFalse(resolvedBaseName.contains("welcome"), "Base name must not contain 'welcome'");
        assertFalse(resolvedBaseName.contains("hospitality_services"), "Base name must not contain spoken prompt text");
        assertEquals("ivr_flow", resolvedBaseName);

        // 2. Assert buildDraftFilename produces clean draft filename
        String draftFilename = FlowDraftService.buildDraftFilename(tenantId, flowId, longGreeting, 1);
        assertEquals("ivr_flow_draft_v1.json", draftFilename);
        assertFalse(draftFilename.contains("welcome"));

        // 3. Test explicit clean business name (e.g. "Hospitality IVR")
        String cleanBusinessName = "Hospitality IVR";
        String cleanBaseName = FlowDraftService.getBaseName(tenantId, flowId, cleanBusinessName);
        assertEquals("hospitality_ivr", cleanBaseName);

        // 4. Publish using clean business name
        FlowModel model = new FlowModel();
        model.setVoicexmlVersion("2.1");
        FlowNode start = new FlowNode("start", FlowNodeType.START, "Start");
        start.setPrompt(new FlowPrompt(longGreeting));
        FlowNode end = new FlowNode("end", FlowNodeType.END, "End Call");
        model.addNode(start);
        model.addNode(end);
        model.addConnection(new FlowConnection("c1", "start", "out", "end", "in"));

        FlowPublishService.FlowPublishResult result = publishService.publishFlow(tenantId, flowId, "1000", cleanBusinessName, exporter.export(model));
        assertTrue(result.isSuccess());

        // Verify VXML scenario file on disk uses clean business name
        Path publishedFile = tempScenariosDir.resolve(tenantId).resolve("hospitality_ivr.vxml");
        assertTrue(Files.exists(publishedFile), "Published file must use clean business name");
        assertFalse(publishedFile.getFileName().toString().contains("welcome"), "Published filename must never contain greeting text");
    }
}
