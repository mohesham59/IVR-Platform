package com.nexusivr.ai.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Portability & Dynamic Path Resolution Tests")
public class PortabilityTest {

    @Test
    @DisplayName("resolveProjectRoot() dynamically resolves existing repository root containing IVR-engine")
    void testResolveProjectRoot() {
        Path root = LlmConfig.resolveProjectRoot();
        assertNotNull(root, "Project root must not be null");
        assertTrue(Files.exists(root), "Project root path must exist on disk: " + root);
        assertTrue(Files.exists(root.resolve("IVR-engine")), "Resolved project root MUST contain IVR-engine folder: " + root);
    }

    @Test
    @DisplayName("Dynamic paths for add_extension.sh, scenarios, and drafts resolve correctly relative to project root")
    void testDynamicEnginePaths() {
        String scriptPathStr = LlmConfig.getAddExtensionScriptPath();
        String scenariosDirStr = LlmConfig.getScenariosDir();
        String draftsDirStr = LlmConfig.getDraftsDir();

        assertNotNull(scriptPathStr, "add_extension.sh path must not be null");
        assertNotNull(scenariosDirStr, "scenarios directory must not be null");
        assertNotNull(draftsDirStr, "drafts directory must not be null");

        Path scriptPath = Paths.get(scriptPathStr);
        Path scenariosDir = Paths.get(scenariosDirStr);
        Path draftsDir = Paths.get(draftsDirStr);

        assertTrue(scriptPathStr.endsWith("add_extension.sh"), "Script path must end with add_extension.sh: " + scriptPathStr);
        assertTrue(scenariosDirStr.endsWith("scenarios"), "Scenarios path must end with scenarios: " + scenariosDirStr);
        assertTrue(draftsDirStr.endsWith("drafts"), "Drafts path must end with drafts: " + draftsDirStr);

        assertTrue(Files.exists(scriptPath), "Resolved add_extension.sh script file MUST exist: " + scriptPath);
    }

    @Test
    @DisplayName("System property ivr.engine.root allows overriding project root dynamically")
    void testCustomProjectRootOverride() {
        Path originalRoot = LlmConfig.resolveProjectRoot();
        try {
            System.setProperty("ivr.engine.root", "/tmp");
            Path customRoot = LlmConfig.resolveProjectRoot();
            assertEquals(Paths.get("/tmp").toAbsolutePath().normalize(), customRoot);
        } finally {
            System.clearProperty("ivr.engine.root");
            assertEquals(originalRoot, LlmConfig.resolveProjectRoot());
        }
    }
}
