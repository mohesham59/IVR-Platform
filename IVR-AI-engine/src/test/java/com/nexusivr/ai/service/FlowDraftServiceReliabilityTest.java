package com.nexusivr.ai.service;

import com.nexusivr.ai.exception.ValidationException;
import com.nexusivr.ai.model.flow.FlowModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FlowDraftServiceReliabilityTest {

    @TempDir
    Path tempDraftsDir;

    @TempDir
    Path tempScenariosDir;

    private FlowDraftService draftService;
    private ModelToVxmlExporter exporter;

    @BeforeEach
    void setUp() {
        exporter = new ModelToVxmlExporter();
        draftService = new FlowDraftService(exporter, tempDraftsDir.toString());
    }

    @Test
    @DisplayName("Saving a draft N times in sequence creates N distinct versioned files on disk without overwriting")
    void testSequentialDraftSavingCreatesDistinctVersionedFiles() throws IOException {
        String tenantId = "00000000_0000_0000_0000_000000000001";
        String flowId = "seq_flow_100";
        String flowName = "Sequential Test Flow";

        int saveCount = 10;
        for (int i = 1; i <= saveCount; i++) {
            String flowJson = String.format("""
                {
                  "name": "Sequential Test Flow",
                  "nodes": [
                    { "id": "n_start", "type": "start", "title": "Start Step %d" },
                    { "id": "n_prompt", "type": "prompt", "title": "Prompt Step %d", "subtitle": "Welcome step %d" }
                  ],
                  "edges": [
                    { "id": "e1", "source": "n_start", "target": "n_prompt" }
                  ]
                }
                """, i, i, i);

            String savedPathStr = draftService.saveDraft(tenantId, flowId, flowName, flowJson);
            assertNotNull(savedPathStr, "Save attempt " + i + " must return a non-null file path");

            Path savedPath = Path.of(savedPathStr);
            assertTrue(Files.exists(savedPath), "Saved file version " + i + " must exist on disk immediately after save");
            assertTrue(Files.size(savedPath) > 0, "Saved file version " + i + " must not be empty (0 bytes)");

            String filename = savedPath.getFileName().toString();
            assertTrue(filename.endsWith("_draft_v" + i + ".json"),
                    "Filename must end with expected version suffix _draft_v" + i + ".json, but got: " + filename);

            String jsonContent = Files.readString(savedPath);
            assertTrue(jsonContent.contains("{"), "File must contain JSON starting curly brace");
            assertTrue(jsonContent.contains("nodes"), "File must contain nodes list key");
            assertTrue(jsonContent.contains("Welcome step " + i), "File must contain step-specific prompt for version " + i);
        }

        // Verify that ALL 10 files exist concurrently in the drafts directory
        List<Path> allFiles;
        try (var stream = Files.list(tempDraftsDir.resolve(tenantId))) {
            allFiles = stream.toList();
        }
        assertEquals(saveCount, allFiles.size(), "Drafts directory must contain exactly " + saveCount + " distinct versioned files");

        // Verify isolation: scenarios directory must remain empty
        List<Path> scenarioFiles;
        try (var stream = Files.list(tempScenariosDir)) {
            scenarioFiles = stream.toList();
        }
        assertTrue(scenarioFiles.isEmpty(), "Scenarios directory must remain completely empty during draft save");
    }

    @Test
    @DisplayName("Invalid flow data without valid nodes fails loudly with ValidationException and creates NO file")
    void testInvalidFlowDataFailsLoudlyWithoutWritingFile() {
        String tenantId = "00000000_0000_0000_0000_000000000001";
        String flowId = "invalid_flow_200";
        String flowName = "Invalid Flow";

        String invalidJson = "{ \"name\": \"Invalid Flow\", \"nodes\": [] }";

        ValidationException ex = assertThrows(ValidationException.class, () ->
                draftService.saveDraft(tenantId, flowId, flowName, invalidJson)
        );

        assertTrue(ex.getMessage().contains("invalid node data") || ex.getMessage().contains("no nodes"),
                "Exception message must clearly inform the user of invalid node data: " + ex.getMessage());

        // Verify no file was written to disk
        Path tenantScopedDir = tempDraftsDir.resolve(tenantId);
        if (Files.exists(tenantScopedDir)) {
            try (var stream = Files.list(tenantScopedDir)) {
                assertEquals(0, stream.count(), "No draft file should be created on disk when save fails validation");
            } catch (IOException e) {
                fail("Directory inspection error: " + e.getMessage());
            }
        }
    }

    @Test
    @DisplayName("Next draft version correctly increments over existing legacy and versioned files")
    void testNextDraftVersionCalculation() throws IOException {
        String tenantId = "00000000_0000_0000_0000_000000000001";
        String flowId = "ver_flow_300";
        String flowName = "Version Calc Flow";

        // Create legacy file using exact base name: <base>_draft.json
        Path legacyFile = tempDraftsDir.resolve(FlowDraftService.buildDraftFilename(tenantId, flowId, flowName, null));
        Files.writeString(legacyFile, "{}");

        int nextVer = FlowDraftService.getNextDraftVersion(tempDraftsDir, tenantId, flowId, flowName);
        assertEquals(2, nextVer, "Next version over legacy file should be 2");

        // Create version 2 file using exact base name: <base>_draft_v2.json
        Path v2File = tempDraftsDir.resolve(FlowDraftService.buildDraftFilename(tenantId, flowId, flowName, 2));
        Files.writeString(v2File, "{}");

        nextVer = FlowDraftService.getNextDraftVersion(tempDraftsDir, tenantId, flowId, flowName);
        assertEquals(3, nextVer, "Next version over v2 file should be 3");
    }

    @Test
    @DisplayName("Draft filenames correctly omit tenant UUID and session-ID fragments, and map to clean business names")
    void testDraftFilenameResolutionAestheticAndScopingRules() {
        String tenantId = "00000000-0000-0000-0000-000000000001";
        String flowId = "flow_a35a7a4d_d0f7d3fb";
        String flowName = "Grievances Department Flow";

        // Generate base name and full draft filename
        String baseName = FlowDraftService.getDraftBaseName(tenantId, flowId, flowName);
        String draftFilename = FlowDraftService.buildDraftFilename(tenantId, flowId, flowName, 1);

        // Assert: No UUID prefix in either name
        assertFalse(baseName.contains(tenantId), "Base name must not contain the tenant UUID");
        assertFalse(draftFilename.contains(tenantId), "Draft filename must not contain the tenant UUID");

        // Assert: No session-ID/flow-ID fragment
        assertFalse(baseName.contains("a35a7a4d"), "Base name must not contain session-ID fragment");
        assertFalse(draftFilename.contains("a35a7a4d"), "Draft filename must not contain session-ID fragment");
        assertFalse(baseName.contains("d0f7d3fb"), "Base name must not contain session-ID fragment");
        assertFalse(draftFilename.contains("d0f7d3fb"), "Draft filename must not contain session-ID fragment");

        // Assert: Correct clean business title slug is generated
        assertEquals("grievances_department_flow", baseName);
        assertEquals("grievances_department_flow_draft_v1.json", draftFilename);
    }
}
