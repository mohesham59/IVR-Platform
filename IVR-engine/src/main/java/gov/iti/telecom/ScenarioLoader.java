package gov.iti.telecom;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ScenarioLoader — reads and caches IVR scenario JSON files.
 *
 * HOW IT WORKS:
 *   1. Given a scenario name (e.g. "restaurant-booking-001"), it reads
 *      the file "{scenariosDir}/restaurant-booking-001.json".
 *   2. The JSON is parsed into a Map (no POJOs needed — keeps things simple).
 *   3. Parsed scenarios are cached in a ConcurrentHashMap so the same file
 *      is only read once, even under concurrent access.
 *
 * THREAD SAFETY:
 *   - ConcurrentHashMap.computeIfAbsent guarantees that the loading function
 *     runs at most once per key, even if multiple threads call it at the
 *     same time. The returned Map is read-only after loading, so no
 *     synchronization is needed for reads.
 */
public class ScenarioLoader {

    // Gson instance (Gson is thread-safe, one instance is enough)
    private static final Gson GSON = new Gson();

    // Type token so Gson knows to parse JSON into Map<String, Object>
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    // Cache: scenario name -> parsed scenario map
    // Using ConcurrentHashMap for safe concurrent access without explicit locking
    private final ConcurrentHashMap<String, Map<String, Object>> cache = new ConcurrentHashMap<>();

    // Filesystem path to the directory containing scenario JSON files
    private final Path scenariosDir;

    /**
     * Creates a loader that reads scenarios from the given directory.
     *
     * @param scenariosDirPath path to the scenarios folder (e.g. "scenarios/")
     */
    public ScenarioLoader(String scenariosDirPath) {
        this.scenariosDir = Paths.get(scenariosDirPath);
    }

    /**
     * Loads and returns the parsed scenario for the given name.
     *
     * If the scenario was already loaded before, it returns the cached copy.
     * If this is the first request (or a concurrent first request), it reads
     * the file exactly once and caches the result.
     *
     * @param scenarioName the scenario name (matches the JSON filename without .json)
     * @return the parsed scenario as a Map with keys like "scenario_id", "nodes", etc.
     * @throws RuntimeException if the file can't be read or parsed
     */
    public Map<String, Object> loadScenario(String scenarioName) {
        // computeIfAbsent is atomic — only one thread will execute the lambda
        // for a given key, even if multiple threads call this concurrently.
        return cache.computeIfAbsent(scenarioName, this::readAndParse);
    }

    /**
     * Reads a JSON file from disk and parses it into a Map.
     * This method is only called once per scenario name (by computeIfAbsent).
     *
     * @param scenarioName the scenario filename (without .json extension)
     * @return parsed scenario Map
     */
    private Map<String, Object> readAndParse(String scenarioName) {
        // Build the full path: scenariosDir/scenarioName.json
        Path filePath = scenariosDir.resolve(scenarioName + ".json");

        System.out.println("[ScenarioLoader] Loading scenario from: " + filePath.toAbsolutePath());

        try (Reader reader = Files.newBufferedReader(filePath)) {
            // Gson parses the entire JSON into a Map<String, Object>.
            // Nested objects become Map<String, Object>, arrays become List<Object>,
            // numbers become Double, strings stay String, booleans stay Boolean.
            Map<String, Object> scenario = GSON.fromJson(reader, MAP_TYPE);

            if (scenario == null || !scenario.containsKey("nodes")) {
                throw new RuntimeException("Invalid scenario file (missing 'nodes'): " + filePath);
            }

            System.out.println("[ScenarioLoader] Loaded scenario: " + scenario.get("scenario_id"));
            return scenario;

        } catch (IOException e) {
            throw new RuntimeException("Failed to read scenario file: " + filePath, e);
        }
    }

    /**
     * Helper: extracts the "nodes" list from a parsed scenario and returns
     * them as a List of Maps (one Map per node).
     *
     * @param scenario the parsed scenario Map
     * @return list of node Maps
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> getNodes(Map<String, Object> scenario) {
        return (List<Map<String, Object>>) scenario.get("nodes");
    }

    /**
     * Helper: finds a node by its "id" field within the nodes list.
     *
     * @param nodes the list of node Maps
     * @param nodeId the id to search for (e.g. "welcome", "main_menu")
     * @return the matching node Map, or null if not found
     */
    public static Map<String, Object> findNodeById(List<Map<String, Object>> nodes, String nodeId) {
        for (Map<String, Object> node : nodes) {
            if (nodeId.equals(node.get("id"))) {
                return node;
            }
        }
        return null; // Node not found
    }
}
