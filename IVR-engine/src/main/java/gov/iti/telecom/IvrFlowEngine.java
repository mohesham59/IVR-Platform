package gov.iti.telecom;

import org.asteriskjava.fastagi.AgiChannel;
import org.asteriskjava.fastagi.AgiRequest;
import org.asteriskjava.fastagi.AgiScript;
import org.asteriskjava.fastagi.AgiServerThread;
import org.asteriskjava.fastagi.DefaultAgiServer;
import org.asteriskjava.fastagi.MappingStrategy;

/**
 * IvrFlowEngine — the main entry point that starts the FastAGI server.
 *
 * HOW IT WORKS:
 * 1. Creates a ScenarioLoader pointing at a configurable scenarios directory.
 * 2. Creates a DefaultAgiServer that listens for AGI connections from Asterisk.
 * 3. Uses a custom MappingStrategy that routes ALL incoming AGI requests
 * to our single IvrAgiScript (which then uses the script name from the
 * URL to pick the right JSON scenario).
 * 4. Wraps the server in an AgiServerThread so it runs in the background.
 *
 * USAGE:
 * java gov.iti.telecom.IvrFlowEngine [scenarios-directory]
 *
 * scenarios-directory: optional path to the folder containing JSON scenario
 * files.
 * Defaults to "scenarios/" in the current working directory.
 *
 * ASTERISK DIALPLAN EXAMPLE:
 * exten => 100,1,AGI(agi://your-java-server:4573/restaurant-booking-001)
 *
 * This will load scenarios/restaurant-booking-001.json and run its flow.
 */
public class IvrFlowEngine {

    // Default directory for scenario JSON files (relative to working directory)
    private static final String DEFAULT_SCENARIOS_DIR = "~/IdeaProjects/IVR_project/IVR_platform/scenarios";

    // Default FastAGI port (Asterisk standard)
    private static final int AGI_PORT = 4573;

    public static void main(String[] args) {
        // --- Step 1: Determine the scenarios directory ---
        // Use command-line argument if provided, otherwise use default
        String scenariosDir = args.length > 0 ? args[0] : DEFAULT_SCENARIOS_DIR;
        System.out.println("==============================================");
        System.out.println("  IVR Flow Engine");
        System.out.println("==============================================");
        System.out.println("  Scenarios directory : " + scenariosDir);
        System.out.println("  AGI port            : " + AGI_PORT);
        System.out.println("==============================================");

        // --- Step 2: Create the shared scenario loaders ---
        ScenarioLoader scenarioLoader = new ScenarioLoader(scenariosDir);
        gov.iti.telecom.vxml.VxmlScenarioLoader vxmlScenarioLoader = new gov.iti.telecom.vxml.VxmlScenarioLoader(scenariosDir);

        // --- Step 3: Create the AGI script that handles calls ---
        IvrAgiScript agiScript = new IvrAgiScript(scenarioLoader, vxmlScenarioLoader);


        // --- Step 4: Set up the FastAGI server ---
        DefaultAgiServer agiServer = new DefaultAgiServer();
        agiServer.setPort(AGI_PORT);

        // Use a mapping strategy that sends ALL requests to our single script.
        // No matter what URL Asterisk sends (e.g. /restaurant-booking-001,
        // /clinic-flow, etc.), it all goes to IvrAgiScript, which then
        // figures out the right JSON file from the script name.
        agiServer.setMappingStrategy(new MappingStrategy() {
            @Override
            public AgiScript determineScript(AgiRequest request, AgiChannel channel) {
                // Always return our single script instance
                return agiScript;
            }
        });

        // --- Step 5: Start the server in a background thread ---
        // AgiServerThread prevents the server from blocking the main thread,
        // though in this case the main thread has nothing else to do.
        AgiServerThread serverThread = new AgiServerThread(agiServer);
        serverThread.startup();

        System.out.println("  FastAGI server is running on port " + AGI_PORT);
        System.out.println("  Waiting for calls from Asterisk...");
        System.out.println("  Press Ctrl+C to stop.");
    }
}
