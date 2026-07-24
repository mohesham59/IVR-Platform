package gov.iti.telecom;

import org.asteriskjava.fastagi.AgiChannel;
import org.asteriskjava.fastagi.AgiException;
import org.asteriskjava.fastagi.AgiRequest;
import org.asteriskjava.fastagi.BaseAgiScript;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IvrAgiScript — the AGI script that handles each incoming call.
 *
 * HOW IT WORKS:
 *   1. Asterisk calls AGI(agi://host/restaurant-booking-001) for a call.
 *   2. The DefaultAgiServer dispatches the call to this script's service() method
 *      on a worker thread from its thread pool.
 *   3. service() uses request.getScript() to get the scenario name
 *      (e.g. "restaurant-booking-001"), loads the matching JSON via ScenarioLoader,
 *      and walks through the nodes from the first one to the end.
 *
 * THREAD SAFETY:
 *   - This class has NO instance fields that hold per-call state.
 *   - All per-call data (collected digits, current node, etc.) lives in
 *     LOCAL VARIABLES inside service(). Local variables are on the thread's
 *     stack, so each concurrent call has its own independent copy.
 *   - The ScenarioLoader is shared but is itself thread-safe (ConcurrentHashMap).
 *   - This means 100 calls to the same extension all work independently.
 *
 * SUPPORTED NODE TYPES:
 *   - "play"     → plays an audio file, then moves to "next"
 *   - "menu"     → plays a prompt, collects 1+ DTMF digits, routes by choice
 *   - "form"     → collects multiple fields (date, time, etc.) one by one
 *   - "transfer" → transfers the call to another SIP destination
 *   - "extension"→ dials a SIP extension and branches on answered / no-answer
 *   - "database" → executes a DB query and branches on result
 */
import gov.iti.telecom.vxml.VxmlDocument;
import gov.iti.telecom.vxml.VxmlInterpreter;
import gov.iti.telecom.vxml.VxmlScenarioLoader;

public class IvrAgiScript extends BaseAgiScript {

    private final ScenarioLoader scenarioLoader;
    private final VxmlScenarioLoader vxmlScenarioLoader;

    public IvrAgiScript(ScenarioLoader scenarioLoader) {
        this.scenarioLoader = scenarioLoader;
        this.vxmlScenarioLoader = new VxmlScenarioLoader("scenarios");
    }

    public IvrAgiScript(ScenarioLoader scenarioLoader, VxmlScenarioLoader vxmlScenarioLoader) {
        this.scenarioLoader = scenarioLoader;
        this.vxmlScenarioLoader = vxmlScenarioLoader;
    }


    /**
     * Called by the AGI server for EACH incoming call, on its own worker thread.
     *
     * Everything inside this method is per-call (local variables only),
     * so concurrent calls never interfere with each other.
     */
    @Override
    public void service(AgiRequest request, AgiChannel channel) throws AgiException {
        // --- Step 1: Figure out which scenario to run ---
        // request.getScript() returns the script path from the AGI URL.
        // e.g. AGI(agi://host/restaurant-booking-001) → "restaurant-booking-001"
        String scriptName = request.getScript();

        // Clean up the script name: remove leading slash and .json/.agi extension if present
        if (scriptName.startsWith("/")) {
            scriptName = scriptName.substring(1);
        }
        if (scriptName.endsWith(".json")) {
            scriptName = scriptName.substring(0, scriptName.length() - 5);
        }
        if (scriptName.endsWith(".agi")) {
            scriptName = scriptName.substring(0, scriptName.length() - 4);
        }

        // Use business_name parameter if provided
        String businessName = request.getParameter("business_name");
        String scenarioName = scriptName;
        if (businessName != null && !businessName.trim().isEmpty()) {
            // Replace spaces with underscores for the filename (e.g., "Tech Support" -> "Tech_Support")
            scenarioName = businessName.trim().replaceAll("\\s+", "_");
        }

        String callerId = request.getCallerIdNumber();
        System.out.println("[IvrAgiScript] New call from " + callerId
                + " on extension " + request.getExtension()
                + " → scenario: " + scenarioName + " (businessName: " + businessName + ")");

        // --- Check for VXML Scenario First ---
        try {
            VxmlDocument vxmlDoc = vxmlScenarioLoader.loadScenario(scenarioName);
            if (vxmlDoc != null) {
                System.out.println("[IvrAgiScript] Executing VoiceXML standard scenario: " + scenarioName);
                answer();
                VxmlInterpreter.execute(vxmlDoc, this, callerId);
                hangup();
                return;
            }
        } catch (Exception e) {
            System.out.println("[IvrAgiScript] VXML scenario not found or fallback to JSON: " + e.getMessage());
        }

        // --- Step 2: Load JSON scenario fallback ---
        Map<String, Object> scenario;
        try {
            scenario = scenarioLoader.loadScenario(scenarioName);
        } catch (RuntimeException e) {
            System.err.println("[IvrAgiScript] Failed to load scenario '" + scenarioName + "': " + e.getMessage());
            // Play a generic error and hang up
            streamFile("tt-somethingwrong");
            hangup();
            return;
        }


        // Get the list of nodes from the scenario
        List<Map<String, Object>> nodes = ScenarioLoader.getNodes(scenario);

        // --- Step 3: Answer the call ---
        answer();

        // --- Step 4: Walk through the flow ---
        // Per-call storage for form data (local variable = thread-safe)
        Map<String, String> callData = new HashMap<>();

        // Start at the first node in the list
        String currentNodeId = (String) nodes.get(0).get("id");

        // Keep processing nodes until we reach the end
        while (currentNodeId != null && !"end".equalsIgnoreCase(currentNodeId)) {

            // Find the current node by its id
            Map<String, Object> node = ScenarioLoader.findNodeById(nodes, currentNodeId);
            if (node == null) {
                System.err.println("[IvrAgiScript] Node not found: " + currentNodeId + " — ending call.");
                break;
            }

            String nodeType = (String) node.get("type");
            System.out.println("[IvrAgiScript] [" + callerId + "] Processing node: "
                    + currentNodeId + " (type=" + nodeType + ")");

// Dispatch to the right handler based on node type
        switch (nodeType) {
            case "play":
                currentNodeId = handlePlayNode(node);
                break;
            case "menu":
                currentNodeId = handleMenuNode(node);
                break;
            case "form":
                currentNodeId = handleFormNode(node, callData, callerId);
                break;
            case "transfer":
                currentNodeId = handleTransferNode(node);
                break;
            case "extension":
                currentNodeId = handleExtensionNode(node);
                break;
            case "database":
                currentNodeId = handleDatabaseNode(node);
                break;
            default:
                System.err.println("[IvrAgiScript] Unknown node type: " + nodeType);
                currentNodeId = (String) node.get("next"); // Try to continue anyway
        }
        }

        // --- Step 5: Clean up ---
        System.out.println("[IvrAgiScript] [" + callerId + "] Call flow complete. Hanging up.");
        hangup();
    }

    // ========================= NODE HANDLERS =========================
    // Each handler processes one node type and returns the NEXT node id.

    /**
     * Handles a "play" node: plays an audio file, then returns the next node id.
     *
     * JSON example:
     *   { "id": "welcome", "type": "play", "audio": "welcome_restaurant.wav", "next": "main_menu" }
     */
    private String handlePlayNode(Map<String, Object> node) throws AgiException {
        String audioFile = (String) node.get("audio");

        // Remove .wav/.gsm extension — Asterisk finds the best format automatically
        if (audioFile != null && audioFile.contains(".")) {
            audioFile = audioFile.substring(0, audioFile.lastIndexOf('.'));
        }

        // Play the audio file (caller can press a key to skip)
        streamFile(audioFile);

        // Move to the next node
        return (String) node.get("next");
    }

    /**
     * Handles a "menu" node: plays an audio file, collects DTMF digit(s),
     * and routes to the appropriate next node based on the caller's choice.
     *
     * JSON example:
     *   {
     *     "id": "main_menu", "type": "menu",
     *     "audio": "main_menu_prompt.wav",
     *     "prompt": "Press 1 for reservations...",
     *     "timeout": 5000, "max_digits": 1,
     *     "choices": { "1": "reservations", "2": "hours_info" },
     *     "invalid": "invalid_choice",
     *     "timeout_node": "repeat_menu"
     *   }
     *
     * - "audio"  = the .wav file Asterisk plays (stored in /var/lib/asterisk/sounds/)
     * - "prompt" = human-readable description (used for logging only, NOT played)
     */
    @SuppressWarnings("unchecked")
    private String handleMenuNode(Map<String, Object> node) throws AgiException {
        // "audio" is the actual filename Asterisk will play
        String audioFile = (String) node.get("audio");
        // "prompt" is just a human-readable label for logging
        String prompt = (String) node.get("prompt");

        // Strip the file extension — Asterisk picks the best format automatically
        if (audioFile != null && audioFile.contains(".")) {
            audioFile = audioFile.substring(0, audioFile.lastIndexOf('.'));
        }

        // Get timeout (Gson parses numbers as Double)
        long timeout = node.containsKey("timeout")
                ? ((Double) node.get("timeout")).longValue()
                : 5000L; // default 5 seconds

        // Get max digits to collect
        int maxDigits = node.containsKey("max_digits")
                ? ((Double) node.get("max_digits")).intValue()
                : 1; // default 1 digit

        // Get the choices map (digit → next node id)
        Map<String, String> choices = (Map<String, String>) node.get("choices");

        System.out.println("[IvrAgiScript] Playing menu audio: " + audioFile
                + " (" + prompt + ")");

        // Play the audio file and collect DTMF digits.
        // getData(file, timeout, maxDigits) plays the file and waits for input.
        String digits = getData(audioFile, timeout, maxDigits);

        System.out.println("[IvrAgiScript] Menu input received: '" + digits + "'");

        // Route based on what the caller pressed
        if (digits == null || digits.isEmpty()) {
            // Timeout — no input received
            System.out.println("[IvrAgiScript] Menu timeout, going to timeout node.");
            return (String) node.get("timeout_node");
        } else if (choices != null && choices.containsKey(digits)) {
            // Valid choice — go to the corresponding node
            return choices.get(digits);
        } else {
            // Invalid input — go to the invalid node
            System.out.println("[IvrAgiScript] Invalid menu choice: " + digits);
            return (String) node.get("invalid");
        }
    }

    /**
     * Handles a "form" node: collects multiple fields from the caller
     * (e.g. date, time, party size) and stores them in the per-call data map.
     *
     * JSON example:
     *   {
     *     "id": "reservations", "type": "form",
     *     "fields": [
     *       { "audio": "enter_date.wav", "prompt": "Enter date MMDD", "length": 4, "var": "res_date" },
     *       { "audio": "enter_time.wav", "prompt": "Enter time HHMM", "length": 4, "var": "res_time" }
     *     ],
     *     "next": "confirm_reservation"
     *   }
     *
     * - "audio"  = the .wav file Asterisk plays for this field
     * - "prompt" = human-readable description (logging only)
     */
    @SuppressWarnings("unchecked")
    private String handleFormNode(Map<String, Object> node, Map<String, String> callData,
                                  String callerId) throws AgiException {

        // Get the list of fields to collect
        List<Map<String, Object>> fields = (List<Map<String, Object>>) node.get("fields");

        if (fields != null) {
            for (Map<String, Object> field : fields) {
                // "audio" is the actual filename Asterisk will play
                String audioFile = (String) field.get("audio");
                // "prompt" is just a human-readable label for logging
                String prompt = (String) field.get("prompt");
                int length = ((Double) field.get("length")).intValue();
                String varName = (String) field.get("var");

                // Strip the file extension
                if (audioFile != null && audioFile.contains(".")) {
                    audioFile = audioFile.substring(0, audioFile.lastIndexOf('.'));
                }

                System.out.println("[IvrAgiScript] [" + callerId + "] Playing field audio: "
                        + audioFile + " (" + prompt + ")");

                // Play the audio file and collect digits
                // Timeout of 10 seconds per field
                String input = getData(audioFile, 10000, length);

                // Store the collected input in our per-call data map
                callData.put(varName, input != null ? input : "");

                System.out.println("[IvrAgiScript] [" + callerId + "] Collected "
                        + varName + " = " + input);
            }
        }

        // Log all collected data for this form
        System.out.println("[IvrAgiScript] [" + callerId + "] Form data: " + callData);

        // Move to the next node
        return (String) node.get("next");
    }

    /**
     * Handles a "transfer" node: transfers the call to another destination
     * (e.g. a SIP extension or phone number).
     *
     * JSON example:
     *   { "id": "transfer_operator", "type": "transfer", "destination": "SIP/101", "next": "end" }
     */
    private String handleTransferNode(Map<String, Object> node) throws AgiException {
        String destination = (String) node.get("destination");

        System.out.println("[IvrAgiScript] Transferring call to: " + destination);

        // Use Asterisk's Transfer application to redirect the call
        exec("Transfer", destination);

        // Move to the next node (usually "end" after a transfer)
        return (String) node.get("next");
    }

    /**
     * Handles an "extension" node: dials a SIP extension and branches based on
     * whether the call was answered or not.
     *
     * JSON example:
     *   {
     *     "id": "operator_ext", "type": "extension",
     *     "extension": "SIP/101",
     *     "answered": "connected_to_operator",
     *     "noanswer": "extension_unavailable"
     *   }
     *
     * - "extension" = the SIP endpoint to dial (e.g., "SIP/101" or "SIP/operator@192.168.1.100")
     * - "answered"  = node id to route to if the call is answered
     * - "noanswer"  = node id to route to if the call is not answered (timeout or busy)
     */
    private String handleExtensionNode(Map<String, Object> node) throws AgiException {
        String extension = (String) node.get("extension");

        System.out.println("[IvrAgiScript] Dialing SIP extension: " + extension);

        // Use Asterisk's Dial application to call the SIP endpoint
        // Dial(SIP/101,30) - 30 second timeout
        exec("Dial", extension + ",30");

        // After Dial completes, check the result
        // The Dial application sets DIALSTATUS channel variable:
        // - ANSWERED: call was answered
        // - BUSY: extension is busy
        // - NOANSWER: no one answered within timeout
        // - CANCEL: caller hung up
        // - CONGESTION: network congestion
        String dialStatus = getVariable("DIALSTATUS");

        System.out.println("[IvrAgiScript] Dial result for " + extension + ": " + dialStatus);

        // Route based on dial status
        if ("ANSWERED".equals(dialStatus)) {
            return (String) node.get("answered");
        } else {
            // For BUSY, NOANSWER, CANCEL, CONGESTION - all go to noanswer path
            return (String) node.get("noanswer");
        }
    }

    /**
     * Handles a "database" node: connects using the URL from JSON, runs a
     * SELECT on the given table/column, stores the result string in a local
     * variable "returnedResult", then branches to the next node.
     *
     * JSON example:
     *   {
     *     "id": "database_get_balance",
     *     "type": "database",
     *     "table": "accounts",
     *     "column": "balance",
     *     "url": "jdbc:postgresql://localhost:5432/bank",
     *     "next": "main_menu"
     *   }
     *
     * - "url"    = JDBC connection string to the database
     * - "table"  = table name to query
     * - "column" = column to select
     * - "next"   = node id to continue to (optional; defaults to "end")
     */
    private String handleDatabaseNode(Map<String, Object> node) throws AgiException {
        String url = (String) node.get("url");
        String table = (String) node.get("table");
        String column = (String) node.get("column");

        System.out.println("[IvrAgiScript] Database lookup → table=" + table
                + ", column=" + column + ", url=" + url);

        // Default result; replaced by the actual query value.
        String returnedResult = "";

        try {
            // Load the driver (adjust to your DB vendor, e.g. org.postgresql.Driver)
            Class.forName("org.postgresql.Driver");

            try (java.sql.Connection conn = java.sql.DriverManager.getConnection(url);
                 java.sql.Statement stmt = conn.createStatement();
                 java.sql.ResultSet rs = stmt.executeQuery(
                         "SELECT " + column + " FROM " + table + " LIMIT 1")) {

                if (rs.next()) {
                    returnedResult = rs.getString(column); // cast to String
                    System.out.println("Database Result: " + returnedResult);
                }
            }
        } catch (Exception e) {
            System.err.println("[IvrAgiScript] DB error: " + e.getMessage());
            returnedResult = "ERROR";
        }

        System.out.println("[IvrAgiScript] returnedResult = " + returnedResult);

        // Continue to the next node, or end if not specified
        String next = (String) node.get("next");
        return next != null ? next : "end";
    }
}
