package com.nexusivr.ai.service;

import com.nexusivr.ai.exception.ServiceException;
import com.nexusivr.ai.exception.ValidationException;
import com.nexusivr.ai.model.flow.FlowConnection;
import com.nexusivr.ai.model.flow.FlowNode;
import com.nexusivr.ai.model.flow.FlowNodeType;
import com.nexusivr.ai.model.flow.FlowModel;
import com.nexusivr.ai.model.flow.FlowPrompt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import com.nexusivr.ai.service.FlowPublishService;


class FlowPublishServiceTest {

    @TempDir
    Path tempScenariosDir;

    private FlowPublishService publishService;
    private ModelToVxmlExporter exporter;

    @BeforeEach
    void setUp() {
        exporter = new ModelToVxmlExporter();
        publishService = new FlowPublishService(exporter, new FlowModelValidator(), tempScenariosDir.toString());
    }

    private FlowModel createSampleValidModel() {
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
    void testPublishFlowCreatesVxmlFileAtExpectedPath() throws IOException {
        FlowModel model = createSampleValidModel();
        String vxmlInput = exporter.export(model);

        String tenantId = "tenant_100";
        String flowId = "flow_clinic_01";
        String extension = "101";

        FlowPublishService.FlowPublishResult result = publishService.publishFlow(tenantId, flowId, extension, "Clinic Flow", vxmlInput);

        assertTrue(result.isSuccess());
        // Filename is derived from resolvedBusinessName (tenant_100_clinic_flow)
        assertEquals("tenant_100_clinic_flow.vxml", result.getFilename());

        Path expectedFile = tempScenariosDir.resolve("tenant_100_clinic_flow.vxml");
        assertTrue(Files.exists(expectedFile), "Published .vxml file must exist in scenarios directory");

        String fileContent = Files.readString(expectedFile);
        assertNotNull(fileContent);
        assertTrue(fileContent.contains("<vxml"));
        assertTrue(fileContent.contains("Welcome to NexusIVR service"));
    }

    @Test
    void testRepublishOverwritesExistingFileWithoutDuplicates() throws IOException {
        FlowModel model1 = createSampleValidModel();
        String vxml1 = exporter.export(model1);

        String tenantId = "tenant_100";
        String flowId = "flow_clinic_01";

        publishService.publishFlow(tenantId, flowId, null, "Clinic Flow", vxml1);
        Path targetPath = tempScenariosDir.resolve("tenant_100_clinic_flow.vxml");
        assertTrue(Files.exists(targetPath));

        // Create updated model
        FlowModel model2 = createSampleValidModel();
        model2.getNodes().get(0).getPrompt().setText("Updated Welcome Message");
        String vxml2 = exporter.export(model2);

        publishService.publishFlow(tenantId, flowId, null, "Clinic Flow", vxml2);

        // Verify directory contains VXML and JSON scenario files (no duplicates)
        try (var stream = Files.list(tempScenariosDir)) {
            long count = stream.count();
            assertEquals(2, count, "Republishing should overwrite existing .vxml and .json files");
        }

        String updatedContent = Files.readString(targetPath);
        assertTrue(updatedContent.contains("Updated Welcome Message"), "File content must reflect updated flow on republish");
    }

    @Test
    void testPublishInvalidFlowThrowsValidationException() {
        // Invalid VoiceXML with missing start node / disconnected elements
        String invalidVxml = "<?xml version=\"1.0\"?><vxml version=\"2.1\"><form id=\"orphan\"><block><prompt>Hello</prompt></block></form></vxml>";

        assertThrows(ValidationException.class, () -> {
            publishService.publishFlow("tenant_100", "flow_1", null, "Invalid Flow", invalidVxml);
        });
    }

    @Test
    void testSanitizeBusinessName() {
        assertEquals("clinic_flow", FlowPublishService.sanitizeBusinessName("Clinic Flow"));
        assertEquals("bank_ivr_2024", FlowPublishService.sanitizeBusinessName("Bank IVR 2024!"));
        assertEquals("pizza_place", FlowPublishService.sanitizeBusinessName("Pizza Place"));
        assertEquals("my_flow", FlowPublishService.sanitizeBusinessName("  my-flow  "));
        assertEquals("published_flow", FlowPublishService.sanitizeBusinessName(null));
        assertEquals("published_flow", FlowPublishService.sanitizeBusinessName("   "));
        assertEquals("published_flow", FlowPublishService.sanitizeBusinessName("!!!"));
        // Long name truncated to 64 chars
        String longName = "a".repeat(100);
        assertEquals(64, FlowPublishService.sanitizeBusinessName(longName).length());
    }

    @Test
    void testPublishHandlesUnwritableDirectoryGracefully() {
        File readOnlyDir = new File(tempScenariosDir.toFile(), "readonly_scenarios");
        assertTrue(readOnlyDir.mkdir());
        readOnlyDir.setWritable(false);

        FlowPublishService serviceWithUnwritableDir = new FlowPublishService(exporter, new FlowModelValidator(), readOnlyDir.getAbsolutePath());
        FlowModel model = createSampleValidModel();
        String vxmlInput = exporter.export(model);

        assertThrows(ServiceException.class, () -> {
            serviceWithUnwritableDir.publishFlow("tenant_100", "flow_1", null, "Test", vxmlInput);
        });

        readOnlyDir.setWritable(true); // reset
    }

    @Test
    void testExecuteAddExtensionScriptHandlesSpacesInPathAndPartialFailure() throws IOException {
        // Create a directory path containing spaces: "IVR-GP /IVR-engine"
        Path folderWithSpace = tempScenariosDir.resolve("IVR-GP ").resolve("IVR-engine");
        Path scenariosFolder = folderWithSpace.resolve("scenarios");
        Files.createDirectories(scenariosFolder);

        // Mock add_extension.sh in parent folder
        Path scriptFile = folderWithSpace.resolve("add_extension.sh");
        String scriptContent = """
                #!/bin/bash
                echo "Adding extension $1 for $2"
                exit 0
                """;
        Files.writeString(scriptFile, scriptContent);
        scriptFile.toFile().setExecutable(true);

        FlowPublishService customService = new FlowPublishService(exporter, new FlowModelValidator(), scenariosFolder.toString());
        FlowPublishService.ScriptExecutionResult result = customService.executeAddExtensionScript("1000", "Pizza Place", scenariosFolder.resolve("test.vxml"));

        assertTrue(result.isSuccess(), "Script execution should succeed when script returns exit code 0");
        assertEquals(0, result.getExitCode());
        // Script now receives the sanitized business name "pizza_place", not raw "Pizza Place"
        assertTrue(result.getStdout().contains("Adding extension 1000 for pizza_place"));
    }

    @Test
    void testPublishFlowWithBoundedRetryLoopSucceeds() throws IOException {
        // Construct a flow model with an intentional bounded retry loop:
        // Start -> Main Menu (menu)
        // Main Menu choice 1 -> Retry Prompt (retry)
        // Retry Prompt -> Main Menu (retry loop)
        // Main Menu choice 2 -> End Call (exit path)
        FlowModel model = new FlowModel();
        model.setVoicexmlVersion("2.1");

        FlowNode startNode = new FlowNode("start", FlowNodeType.START, "Start Call");
        startNode.setPrompt(new FlowPrompt("Welcome to Pizza Palace"));

        FlowNode menuNode = new FlowNode("menu", FlowNodeType.MENU, "Main Menu");
        menuNode.setPrompt(new FlowPrompt("Press 1 to order, or press 2 to retry"));

        FlowNode retryNode = new FlowNode("retry", FlowNodeType.PROMPT, "Retry Prompt");
        retryNode.setPrompt(new FlowPrompt("Invalid option, please try again"));

        FlowNode endNode = new FlowNode("end", FlowNodeType.END, "End Call");

        model.addNode(startNode);
        model.addNode(menuNode);
        model.addNode(retryNode);
        model.addNode(endNode);

        model.addConnection(new FlowConnection("c1", "start", "out", "menu", "in"));
        model.addConnection(new FlowConnection("c2", "menu", "key1", "retry", "in"));
        model.addConnection(new FlowConnection("c3", "retry", "out", "menu", "in")); // retry loop back-edge
        model.addConnection(new FlowConnection("c4", "menu", "key2", "end", "in"));  // exit path to End Call!

        String vxml = exporter.export(model);

        // Bounded retry loop must NOT be rejected by Publish validation
        FlowPublishService.FlowPublishResult result = publishService.publishFlow("tenant_100", "flow_pizza", "1002", "Pizza Palace", vxml);
        assertTrue(result.isSuccess(), "Publish must succeed for IVR flows with bounded retry loops");
    }

    @Test
    void testPublishFlowWithDeadEndCycleFailsWithHumanReadableMessage() {
        // Construct a flow model with a dead-end cycle:
        // Start -> Condition (cond)
        // Condition true -> End Call (end)
        // Condition false -> Main Menu (menu) -> Retry Prompt (retry) -> Main Menu (dead-end cycle!)
        FlowModel model = new FlowModel();
        model.setVoicexmlVersion("2.1");

        FlowNode startNode = new FlowNode("start", FlowNodeType.START, "Start Call");
        startNode.setPrompt(new FlowPrompt("Start prompt"));

        FlowNode condNode = new FlowNode("cond", FlowNodeType.CONDITION, "Check Hours");
        condNode.setPrompt(new FlowPrompt("Checking business hours"));

        FlowNode menuNode = new FlowNode("menu", FlowNodeType.MENU, "Main Menu");
        menuNode.setPrompt(new FlowPrompt("Main Menu prompt"));

        FlowNode retryNode = new FlowNode("retry", FlowNodeType.PROMPT, "Retry Prompt");
        retryNode.setPrompt(new FlowPrompt("Retry prompt"));

        FlowNode endNode = new FlowNode("end", FlowNodeType.END, "End Call");

        model.addNode(startNode);
        model.addNode(condNode);
        model.addNode(menuNode);
        model.addNode(retryNode);
        model.addNode(endNode);

        model.addConnection(new FlowConnection("c1", "start", "out", "cond", "in"));
        model.addConnection(new FlowConnection("c2", "cond", "true", "end", "in"));
        model.addConnection(new FlowConnection("c3", "cond", "false", "menu", "in"));
        model.addConnection(new FlowConnection("c4", "menu", "key1", "retry", "in"));
        model.addConnection(new FlowConnection("c5", "retry", "out", "menu", "in")); // dead-end cycle!

        String vxml = exporter.export(model);

        ValidationException ex = assertThrows(ValidationException.class, () -> {
            publishService.publishFlow("tenant_100", "flow_pizza", "1002", "Pizza Palace", vxml);
        });

        assertTrue(ex.getMessage().contains("Dead-end cycle with no path to End Call detected"),
                "Exception message should mention dead-end cycle, got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("Main Menu -> Retry Prompt -> Main Menu"),
                "Exception message must include human-readable cycle path with node titles, got: " + ex.getMessage());

    }

    @Test
    void testResolveOrAllocateExtension() throws IOException {
        Path mockConf = tempScenariosDir.resolve("extensions.conf");

        // Case 1: Explicit extension specified
        assertEquals("1005", FlowPublishService.resolveOrAllocateExtension("1005", "Clinic Flow", mockConf));

        // Case 2: File doesn't exist -> defaults to 1000
        assertEquals("1000", FlowPublishService.resolveOrAllocateExtension(null, "Clinic Flow", mockConf));

        // Case 3: File exists with extension 1000 for clinic_flow -> reuses 1000
        String confContent = """
                [default]
                exten => 1000,1,NoOp(Incoming call for clinic_flow)
                exten => 1000,n,AGI(agi://127.0.0.1:4573/ivr_platform?business_name=clinic_flow)
                exten => 1000,n,Hangup()
                """;
        Files.writeString(mockConf, confContent);
        assertEquals("1000", FlowPublishService.resolveOrAllocateExtension(null, "Clinic Flow", mockConf));

        // Case 4: File exists with highest extension 1002 -> allocates 1003 for new business
        String confContent2 = """
                [default]
                exten => 1000,1,NoOp(Incoming call for clinic_flow)
                exten => 1002,1,NoOp(Incoming call for pizza_palace)
                """;
        Files.writeString(mockConf, confContent2);
        assertEquals("1003", FlowPublishService.resolveOrAllocateExtension(null, "Bank IVR", mockConf));
    }

    @Test
    void testExecuteAddExtensionScriptSurfacesActionableFailureOutput() throws IOException {
        Path folderWithSpace = tempScenariosDir.resolve("IVR-GP-test").resolve("IVR-engine");
        Path scenariosFolder = folderWithSpace.resolve("scenarios");
        Files.createDirectories(scenariosFolder);

        // Mock add_extension.sh that fails with permission error
        Path scriptFile = folderWithSpace.resolve("add_extension.sh");
        String scriptContent = """
                #!/bin/bash
                echo "Error: Cannot write to /etc/asterisk/extensions.conf. Please ensure the current user has write permission."
                exit 1
                """;
        Files.writeString(scriptFile, scriptContent);
        scriptFile.toFile().setExecutable(true);

        FlowPublishService customService = new FlowPublishService(exporter, new FlowModelValidator(), scenariosFolder.toString());
        FlowPublishService.FlowPublishResult result = customService.publishFlow("tenant_100", "flow_1", "1000", "Test Flow", exporter.export(createSampleValidModel()));

        assertFalse(result.isExtensionRegistered());
        assertTrue(result.getExtensionMessage().contains("exit code 1"));
        assertTrue(result.getExtensionMessage().contains("Cannot write to /etc/asterisk/extensions.conf"));
    }

    @Test
    void testFilenameAndScriptBusinessArgumentExactMatch() throws IOException {
        Path folderWithSpace = tempScenariosDir.resolve("IVR-GP-match").resolve("IVR-engine");
        Path scenariosFolder = folderWithSpace.resolve("scenarios");
        Files.createDirectories(scenariosFolder);

        // Mock add_extension.sh that prints its received extension ($1) and business ($2) arguments
        Path scriptFile = folderWithSpace.resolve("add_extension.sh");
        String scriptContent = """
                #!/bin/bash
                echo "REGISTERED_EXT=$1 REGISTERED_BUSINESS=$2"
                exit 0
                """;
        Files.writeString(scriptFile, scriptContent);
        scriptFile.toFile().setExecutable(true);

        FlowPublishService customService = new FlowPublishService(exporter, new FlowModelValidator(), scenariosFolder.toString());

        String tenantId = "tenant_100";
        String flowId = "flow_clinic_01";
        String flowName = "Clinic Flow";

        FlowPublishService.FlowPublishResult result = customService.publishFlow(tenantId, flowId, "1000", flowName, exporter.export(createSampleValidModel()));

        assertTrue(result.isSuccess());

        // 1. Verify the VXML output filename base matches resolved business name
        String expectedBaseName = FlowPublishService.resolveBusinessName(tenantId, flowId, flowName);
        assertEquals("tenant_100_clinic_flow", expectedBaseName);
        assertEquals(expectedBaseName + ".vxml", result.getFilename());

        // 2. Verify scenario VXML and JSON files exist on disk with exact base name
        assertTrue(Files.exists(scenariosFolder.resolve(expectedBaseName + ".vxml")), "VXML scenario file must exist with exact business name");
        assertTrue(Files.exists(scenariosFolder.resolve(expectedBaseName + ".json")), "JSON scenario file must exist with exact business name");

        // 3. Verify the business argument passed to add_extension.sh equals expectedBaseName BYTE-FOR-BYTE
        assertTrue(result.getExtensionMessage().contains("REGISTERED_BUSINESS=" + expectedBaseName),
                "add_extension.sh business argument must equal scenario filename base byte-for-byte! Got: " + result.getExtensionMessage());
    }
}
