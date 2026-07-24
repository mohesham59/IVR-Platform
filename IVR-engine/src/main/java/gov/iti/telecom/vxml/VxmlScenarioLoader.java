package gov.iti.telecom.vxml;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VxmlScenarioLoader — Loads and caches VoiceXML 2.1 scenarios (.vxml / .xml files).
 */
public class VxmlScenarioLoader {

    private final ConcurrentHashMap<String, VxmlDocument> cache = new ConcurrentHashMap<>();
    private final Path scenariosDir;

    public VxmlScenarioLoader(String scenariosDirPath) {
        this.scenariosDir = Paths.get(scenariosDirPath);
    }

    public VxmlDocument loadScenario(String scenarioName) {
        return cache.computeIfAbsent(scenarioName, this::readAndParse);
    }

    private VxmlDocument readAndParse(String scenarioName) {
        Path vxmlPath = scenariosDir.resolve(scenarioName + ".vxml");
        File file = vxmlPath.toFile();

        if (!file.exists()) {
            vxmlPath = scenariosDir.resolve(scenarioName + ".xml");
            file = vxmlPath.toFile();
        }

        if (!file.exists()) {
            throw new RuntimeException("VoiceXML scenario file not found: " + scenarioName + " (.vxml / .xml) in " + scenariosDir.toAbsolutePath());
        }

        System.out.println("[VxmlScenarioLoader] Loading VoiceXML scenario from: " + file.getAbsolutePath());

        try {
            VxmlDocument doc = VxmlParser.parse(file);
            System.out.println("[VxmlScenarioLoader] Successfully loaded VXML scenario: " + scenarioName);
            return doc;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse VoiceXML scenario file: " + file.getAbsolutePath(), e);
        }
    }
}
