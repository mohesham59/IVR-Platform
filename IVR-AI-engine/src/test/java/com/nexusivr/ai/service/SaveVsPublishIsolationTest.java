package com.nexusivr.ai.service;

import com.nexusivr.ai.dao.FlowDao;
import com.nexusivr.ai.model.Flow;
import com.nexusivr.ai.model.flow.FlowConnection;
import com.nexusivr.ai.model.flow.FlowNode;
import com.nexusivr.ai.model.flow.FlowNodeType;
import com.nexusivr.ai.model.flow.FlowModel;
import com.nexusivr.ai.model.flow.FlowPrompt;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SaveVsPublishIsolationTest {

    @TempDir
    Path tempScenariosDir; // IVR_ENGINE_SCENARIOS_DIR

    @TempDir
    Path tempDraftsDir; // IVR_ENGINE_DRAFTS_DIR

    private FlowPublishService publishService;
    private FlowDraftService draftService;
    private ModelToVxmlExporter exporter;
    private Gson gson;

    @BeforeEach
    void setUp() {
        exporter = new ModelToVxmlExporter();
        publishService = new FlowPublishService(exporter, new FlowModelValidator(), tempScenariosDir.toString());
        draftService = new FlowDraftService(exporter, tempDraftsDir.toString());
        gson = new Gson();
    }

    private FlowModel createSampleFlowModel() {
        FlowModel model = new FlowModel();
        model.setVoicexmlVersion("2.1");

        FlowNode startNode = new FlowNode("start", FlowNodeType.START, "Welcome");
        startNode.setPrompt(new FlowPrompt("Welcome to NexusIVR service"));

        FlowNode endNode = new FlowNode("end", FlowNodeType.END, "End Call");
        endNode.setPrompt(new FlowPrompt("Thank you for calling. Goodbye."));

        model.addNode(startNode);
        model.addNode(endNode);
        model.addConnection(new FlowConnection("c1", "start", "out", "end", "in"));
        return model;
    }

    @Test
    void testSaveDraftsGeneratesVersionedVxmlAndDoesNotModifyEngineScenariosDirectory() throws IOException {
        FlowModel model = createSampleFlowModel();
        String flowJson = gson.toJson(model);

        String tenantId = "tenant_test";
        String flowId = "flow_draft_123";
        String flowName = "Customer Support Flow";

        // Perform 4 consecutive Save (Draft) operations
        for (int i = 1; i <= 4; i++) {
            String draftPath = draftService.saveDraft(tenantId, flowId, flowName, flowJson);
            assertNotNull(draftPath);
            assertTrue(draftPath.startsWith(tempDraftsDir.toAbsolutePath().toString()), "Draft must be written to IVR_ENGINE_DRAFTS_DIR");
            String expectedEnd = FlowDraftService.buildDraftFilename(tenantId, flowId, flowName, i);
            assertTrue(draftPath.endsWith(expectedEnd),
                    "Draft file must include version v" + i + ", got: " + draftPath);
        }

        // Assert: IVR_ENGINE_SCENARIOS_DIR contains 0 files after multiple saves
        try (var stream = Files.list(tempScenariosDir)) {
            long scenariosCount = stream.count();
            assertEquals(0, scenariosCount, "IVR_ENGINE_SCENARIOS_DIR must contain ZERO files after Save (Draft) operations");
        }

        // Assert: IVR_ENGINE_DRAFTS_DIR contains all 4 saved draft VXML files
        try (var stream = Files.list(tempDraftsDir.resolve(tenantId))) {
            List<Path> draftFiles = stream.toList();
            assertEquals(4, draftFiles.size(), "IVR_ENGINE_DRAFTS_DIR should contain 4 saved draft version files");
            
            Path v4File = tempDraftsDir.resolve(tenantId).resolve(FlowDraftService.buildDraftFilename(tenantId, flowId, flowName, 4));
            assertTrue(Files.exists(v4File));
            String content = Files.readString(v4File);
            assertTrue(content.contains("nodes"), "Draft file must contain JSON nodes");
            assertTrue(content.contains("Welcome to NexusIVR service"), "Draft file must contain node prompts");
        }
    }

    @Test
    void testOnlyPublishPopulatesEngineScenariosDirectory() throws IOException {
        FlowModel model = createSampleFlowModel();
        String vxml = exporter.export(model);

        String tenantId = "tenant_test";
        String flowId = "flow_pub_456";
        String flowName = "Billing Flow";

        // Save multiple drafts first
        for (int i = 0; i < 3; i++) {
            draftService.saveDraft(tenantId, flowId, flowName, gson.toJson(model));
        }

        // Assert scenarios directory still empty
        try (var stream = Files.list(tempScenariosDir)) {
            assertEquals(0, stream.count(), "Scenarios directory must remain empty before explicit Publish");
        }

        // Now explicitly Publish
        FlowPublishService.FlowPublishResult result = publishService.publishFlow(tenantId, flowId, "102", flowName, vxml);
        assertTrue(result.isSuccess());

        // Assert scenarios directory contains exactly 1 VXML file after Publish
        try (var stream = Files.list(tempScenariosDir.resolve(tenantId))) {
            long count = stream.filter(p -> p.getFileName().toString().endsWith(".vxml")).count();
            assertEquals(1, count, "IVR_ENGINE_SCENARIOS_DIR must contain exactly 1 VXML file after explicit Publish");
        }

        Path publishedFile = tempScenariosDir.resolve(tenantId).resolve("billing_flow.vxml");
        assertTrue(Files.exists(publishedFile), "Published file must be in IVR_ENGINE_SCENARIOS_DIR == " + publishedFile);
        String content = Files.readString(publishedFile);
        assertTrue(content.contains("<vxml"), "Published scenario file must contain valid VXML markup");
        assertTrue(content.contains("Welcome to NexusIVR service"), "Published scenario file must contain node prompts");
    }

    @Test
    void testFlowServiceSavePersistsDbModelAndDraftVxmlFileAndGracefullyHandlesWriteErrors() {
        FlowDao mockFlowDao = mock(FlowDao.class);
        AiService mockAiService = mock(AiService.class);

        UUID tenantId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();

        FlowModel model = createSampleFlowModel();
        Flow flowInput = new Flow();
        flowInput.setId(flowId);
        flowInput.setName("Skyways Airlines");
        flowInput.setFlowJson(gson.toJson(model));

        when(mockFlowDao.create(any(Flow.class))).thenReturn(flowInput);
        when(mockFlowDao.update(any(UUID.class), any(UUID.class), any(Flow.class))).thenReturn(true);

        FlowService flowService = new FlowService(mockFlowDao, mockAiService, draftService);

        // 1. Create Flow -> DB persisted and draft file created
        Flow created = flowService.createFlow(tenantId, flowInput);
        assertNotNull(created);
        verify(mockFlowDao, times(1)).create(any(Flow.class));

        Path draftV1 = tempDraftsDir.resolve(tenantId.toString()).resolve(FlowDraftService.buildDraftFilename(tenantId.toString(), flowId.toString(), "Skyways Airlines", 1));
        assertTrue(Files.exists(draftV1), "Draft v1 file should exist after createFlow at: " + draftV1);

        // 2. Update Flow -> DB updated and draft v2 file created
        Flow updated = flowService.updateFlow(flowId, tenantId, flowInput);
        assertNotNull(updated);
        verify(mockFlowDao, times(1)).update(eq(flowId), eq(tenantId), any(Flow.class));

        Path draftV2 = tempDraftsDir.resolve(tenantId.toString()).resolve(FlowDraftService.buildDraftFilename(tenantId.toString(), flowId.toString(), "Skyways Airlines", 2));
        assertTrue(Files.exists(draftV2), "Draft v2 file should exist after updateFlow at: " + draftV2);

        // 3. Graceful failure test: draft directory write fails, but DB update STILL succeeds
        FlowDraftService failingDraftService = new FlowDraftService(exporter, "/invalid_nonexistent_directory_for_test_123");
        FlowService flowServiceWithFailingDrafts = new FlowService(mockFlowDao, mockAiService, failingDraftService);

        Flow resultWhenWriteFails = assertDoesNotThrow(() -> flowServiceWithFailingDrafts.updateFlow(flowId, tenantId, flowInput),
                "Flow update must NOT fail if writing the draft file encounters an error");
        assertNotNull(resultWhenWriteFails);
        verify(mockFlowDao, times(2)).update(eq(flowId), eq(tenantId), any(Flow.class));
    }
}
