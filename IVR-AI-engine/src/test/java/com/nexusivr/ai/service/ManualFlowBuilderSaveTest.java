package com.nexusivr.ai.service;

import com.nexusivr.ai.model.flow.FlowModel;
import com.nexusivr.ai.model.flow.FlowNode;
import com.nexusivr.ai.model.flow.FlowNodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ManualFlowBuilderSaveTest {

    @TempDir
    Path tempDraftsDir;

    @TempDir
    Path tempScenariosDir;

    private FlowDraftService draftService;
    private FlowPublishService publishService;

    @BeforeEach
    void setUp() {
        ModelToVxmlExporter exporter = new ModelToVxmlExporter();
        FlowModelValidator validator = new FlowModelValidator();
        draftService = new FlowDraftService(exporter, tempDraftsDir.toString());
        publishService = new FlowPublishService(exporter, validator, tempScenariosDir.toString());
    }

    @Test
    @DisplayName("Manually constructed builder React Flow JSON with custom node types saves cleanly as VoiceXML draft")
    void testManualBuilderFlowSavesCleanVxmlDraft() throws IOException {
        String tenantId = "00000000_0000_0000_0000_000000000001";
        String flowId = "manual_flow_999";
        String flowName = "Manual Clinic IVR";

        // React Flow JSON sent directly from the frontend builder when nodes are dragged onto canvas
        String reactFlowJson = """
            {
              "name": "Manual Clinic IVR",
              "nodes": [
                { "id": "n_start", "type": "start", "title": "Start" },
                { "id": "n_greeting", "type": "greeting", "title": "Welcome Greeting", "subtitle": "Hello and welcome" },
                { "id": "n_menu", "type": "dtmf_menu", "title": "Main Menu", "ports": [{ "id": "key1", "label": "Appointments" }] },
                { "id": "n_hours", "type": "hours", "title": "Check Hours" },
                { "id": "n_ext", "type": "extension", "title": "Doctor Extension" },
                { "id": "n_rec", "type": "record", "title": "Record Message" },
                { "id": "n_vm", "type": "voicemail", "title": "Leave Voicemail" },
                { "id": "n_end", "type": "end", "title": "End Call" }
              ],
              "edges": [
                { "id": "e1", "source": "n_start", "target": "n_greeting" },
                { "id": "e2", "source": "n_greeting", "target": "n_menu" },
                { "id": "e3", "source": "n_menu", "target": "n_hours", "sourcePort": "key1" },
                { "id": "e4", "source": "n_hours", "target": "n_ext", "sourcePort": "open" },
                { "id": "e5", "source": "n_hours", "target": "n_rec", "sourcePort": "closed" },
                { "id": "e6", "source": "n_rec", "target": "n_vm" },
                { "id": "e7", "source": "n_vm", "target": "n_end" }
              ]
            }
            """;

        // 1. Assert saveDraft completes cleanly without throwing NPE or writing raw JSON fallback
        String savedPathStr = draftService.saveDraft(tenantId, flowId, flowName, reactFlowJson, 1);
        assertNotNull(savedPathStr);

        Path savedPath = Path.of(savedPathStr);
        assertTrue(Files.exists(savedPath), "Saved draft file must exist on disk");

        String savedContent = Files.readString(savedPath);
        assertTrue(savedContent.contains("\"nodes\""), "Saved content must contain nodes list key");
        assertTrue(savedContent.contains("Manual Clinic IVR"), "Saved content must contain flow name");

        // 2. Reload saved JSON draft via FlowContextService and verify nodes are parsed cleanly
        FlowModel reloadedModel = FlowContextService.convertJsonToModel(savedContent);
        assertNotNull(reloadedModel, "Reloaded JSON draft must parse back into FlowModel");

        assertFalse(reloadedModel.getNodes().isEmpty(), "Reloaded model must contain nodes");
        for (FlowNode node : reloadedModel.getNodes()) {
            assertNotNull(node.getType(), "Every reloaded node must have a non-null FlowNodeType!");
        }

        // 3. Assert publish completes cleanly as well
        FlowPublishService.FlowPublishResult pubResult = publishService.publishFlow(tenantId, flowId, "2000", flowName, reactFlowJson);
        assertTrue(pubResult.isSuccess(), "Publishing manual flow must succeed");

        Path pubFile = tempScenariosDir.resolve(tenantId).resolve("manual_clinic_ivr.vxml");
        assertTrue(Files.exists(pubFile), "Published VXML scenario file must exist on disk");
        String pubContent = Files.readString(pubFile);
        assertTrue(pubContent.contains("<vxml"), "Published scenario must be genuine VoiceXML");
    }
}
