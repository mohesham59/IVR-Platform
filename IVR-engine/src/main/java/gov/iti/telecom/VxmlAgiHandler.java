package gov.iti.telecom;

import org.asteriskjava.fastagi.AgiChannel;
import org.asteriskjava.fastagi.AgiException;
import org.asteriskjava.fastagi.AgiRequest;
import org.asteriskjava.fastagi.BaseAgiScript;
import org.jvoicexml.ConnectionInformation;
import org.jvoicexml.Session;
import org.jvoicexml.event.ErrorEvent;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * VxmlAgiHandler — intelligent FastAGI handler for dynamic VXML execution.
 *
 * <h2>HOW IT WORKS</h2>
 * <ol>
 *   <li>Receives FastAGI call from Asterisk (e.g., agi://127.0.0.1:4573/hello)</li>
 *   <li>Extracts VXML name from:
 *       <ul>
 *         <li>VXML_FILE Asterisk variable (if set)</li>
 *         <li>AGI request path (e.g., "/hello" → "hello.vxml")</li>
 *         <li>Default fallback: "hello.vxml"</li>
 *       </ul>
 *   </li>
 *   <li>Loads the VXML using VxmlLoader</li>
 *   <li>Executes it via VxmlScenarioEngine</li>
 *   <li>Collects results (DTMF input, form data) back to Asterisk variables</li>
 * </ol>
 *
 * <h2>USAGE FROM ASTERISK</h2>
 * <pre>
 * ; Simple: use path as VXML name
 * exten => 500,1,NoOp(Call hello.vxml)
 * same  => n,AGI(agi://127.0.0.1:4573/hello)
 * same  => n,Hangup()
 *
 * ; Advanced: override VXML name via variable
 * exten => 501,1,NoOp(Call any VXML)
 * same  => n,Set(VXML_FILE=menu-example)
 * same  => n,AGI(agi://127.0.0.1:4573/dynamic)
 * same  => n,Hangup()
 * </pre>
 *
 * <h2>ASTERISK VARIABLES SET BY THIS HANDLER</h2>
 * After execution, these variables are available for use in the dialplan:
 * <ul>
 *   <li>VXML_SESSION_ID: Unique session identifier</li>
 *   <li>VXML_STATE: Final session state (COMPLETED, ERROR, etc.)</li>
 *   <li>VXML_ERROR: Error message (if any)</li>
 *   <li>VXML_RESULT_*: Form results (e.g., VXML_RESULT_user_choice)</li>
 * </ul>
 *
 * <h2>EXAMPLE DIALPLAN USAGE</h2>
 * <pre>
 * exten => 500,1,Set(VXML_FILE=hello)
 * same  => n,AGI(agi://127.0.0.1:4573/hello)
 * same  => n,NoOp(Session: ${VXML_SESSION_ID} State: ${VXML_STATE})
 * same  => n,GotoIf($["${VXML_STATE}" = "COMPLETED"]?success:error)
 * same  => n(success),Playback(demo-thanks)
 * same  => n,Hangup()
 * same  => n(error),Playback(vm-goodbye)
 * same  => n,Hangup()
 * </pre>
 *
 * @author IVR Platform Team
 * @version 1.0
 * @see VxmlScenarioEngine
 * @see VxmlLoader
 */
public class VxmlAgiHandler extends BaseAgiScript {

    private static final org.slf4j.Logger logger = 
            org.slf4j.LoggerFactory.getLogger(VxmlAgiHandler.class);

    // Shared VxmlScenarioEngine instance (initialized once)
    private static volatile VxmlScenarioEngine vxmlEngine;
    private static final Object ENGINE_LOCK = new Object();

    @Override
    public void service(AgiRequest request, AgiChannel channel) throws AgiException {
        String callerId = "unknown";
        String vxmlName = "hello";  // Default VXML
        String sessionId = null;

        try {
            // Extract caller information
            callerId = request.getCallerId() != null ? 
                    request.getCallerId() : request.getCallerIdName();
            System.out.println("\n[VxmlAgiHandler] *** New Call from: " + callerId + " ***");

            // Step 1: Determine which VXML to execute
            vxmlName = determineVxmlName(request, channel);
            System.out.println("[VxmlAgiHandler] VXML selected: " + vxmlName);

            // Step 2: Create session ID
            sessionId = createSessionId(callerId, request);
            System.out.println("[VxmlAgiHandler] Session ID: " + sessionId);

            // Step 3: Initialize VXML engine if needed
            initializeEngine();

            // Step 4: Create connection information for this call
            ConnectionInformation connInfo = createConnectionInfo(request, callerId);

            // Step 5: Execute VXML scenario
            System.out.println("[VxmlAgiHandler] Executing VXML: " + vxmlName);
            VxmlSession vxmlSession = executeVxmlScenario(vxmlName, connInfo, sessionId);

            // Step 6: Set Asterisk variables with results
            setAsteriskVariables(channel, vxmlSession);

            // Step 7: Handle post-execution logic (transfers, etc.)
            handlePostExecution(channel, vxmlSession);

            System.out.println("[VxmlAgiHandler] Call completed successfully");

        } catch (Exception e) {
            logger.error("[VxmlAgiHandler] Exception: " + e.getMessage(), e);
            handleError(channel, sessionId, e);
        }
    }

    /**
     * Determines which VXML file to execute.
     *
     * Priority:
     * 1. VXML_FILE Asterisk variable (if caller set it)
     * 2. AGI request path (e.g., "/hello" from "agi://127.0.0.1:4573/hello")
     * 3. Default: "hello"
     *
     * @param request AGI request from Asterisk
     * @param channel AGI channel
     * @return VXML name without extension (e.g., "hello", "menu-example")
     */
    private String determineVxmlName(AgiRequest request, AgiChannel channel) 
            throws AgiException {
        
        // Check for VXML_FILE variable first (highest priority)
        try {
            String vxmlFile = channel.getVariable("VXML_FILE");
            if (vxmlFile != null && !vxmlFile.isEmpty() && !vxmlFile.equals("0")) {
                System.out.println("[VxmlAgiHandler] Using VXML_FILE variable: " + vxmlFile);
                return vxmlFile;
            }
        } catch (AgiException e) {
            System.out.println("[VxmlAgiHandler] Could not read VXML_FILE variable: " + e.getMessage());
        }

        // Extract from AGI request path
        String requestPath = request.getRequestURL();  // e.g., "agi://127.0.0.1:4573/hello"
        if (requestPath != null) {
            // Extract path after last slash
            int lastSlash = requestPath.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < requestPath.length() - 1) {
                String pathVxml = requestPath.substring(lastSlash + 1).trim();
                if (!pathVxml.isEmpty()) {
                    System.out.println("[VxmlAgiHandler] Using path-derived VXML: " + pathVxml);
                    return pathVxml;
                }
            }
        }

        // Check request path from AGI
        String script = request.getScript();  // May also contain path info
        if (script != null && !script.isEmpty()) {
            System.out.println("[VxmlAgiHandler] Using script path: " + script);
            return script;
        }

        // Default fallback
        System.out.println("[VxmlAgiHandler] Using default VXML: hello");
        return "hello";
    }

    /**
     * Creates a unique session ID for this call.
     *
     * Format: {callerId}_{timestamp}_{random}
     * Example: 1001_1719239400_8371
     *
     * @param callerId caller's extension/number
     * @param request AGI request
     * @return session ID
     */
    private String createSessionId(String callerId, AgiRequest request) {
        long timestamp = System.currentTimeMillis() / 1000;
        int random = (int) (Math.random() * 10000);
        String sessionId = callerId + "_" + timestamp + "_" + random;
        return sessionId;
    }

    /**
     * Creates connection information for JVoiceXML.
     *
     * @param request AGI request
     * @param callerId caller identifier
     * @return ConnectionInformation for VXML execution
     */
    private ConnectionInformation createConnectionInfo(AgiRequest request, String callerId) {
        try {
            return new gov.iti.telecom.platform.AsteriskConnectionInformation(
                    "default",                          // profile
                    "asterisk-output",                  // system output
                    "asterisk-input",                   // user input
                    "asterisk-call-control",            // call control
                    new URI("sip:" + callerId),          // called device (caller)
                    new URI("sip:ivr@asterisk"),         // calling device (IVR)
                    "SIP",                              // protocol
                    "2.0"                               // protocol version
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create connection info", e);
        }
    }

    /**
     * Initializes the shared VXML execution engine.
     *
     * Uses double-checked locking for thread-safe singleton initialization.
     *
     * @throws Exception if engine initialization fails
     */
    private void initializeEngine() throws Exception {
        if (vxmlEngine == null) {
            synchronized (ENGINE_LOCK) {
                if (vxmlEngine == null) {
                    System.out.println("[VxmlAgiHandler] Initializing VXML engine...");
                    vxmlEngine = new VxmlScenarioEngine();
                    vxmlEngine.initialize();
                    System.out.println("[VxmlAgiHandler] VXML engine initialized successfully");
                }
            }
        }
    }

    /**
     * Executes a VXML scenario and returns the session results.
     *
     * @param vxmlName VXML file name (without extension)
     * @param connInfo Asterisk connection information
     * @param sessionId unique session identifier
     * @return VxmlSession with execution results
     * @throws Exception if execution fails
     */
    private VxmlSession executeVxmlScenario(String vxmlName, 
                                            ConnectionInformation connInfo,
                                            String sessionId) throws Exception {
        try {
            System.out.println("[VxmlAgiHandler] Starting VXML execution: " + vxmlName);
            VxmlSession session = vxmlEngine.executeVxml(vxmlName, connInfo);
            
            // Override session ID with AGI-based ID
            if (session != null) {
                // Store the AGI session ID in session variables
                session.setVariable("agi_session_id", sessionId);
            }
            
            return session;
        } catch (Exception e) {
            System.err.println("[VxmlAgiHandler] VXML execution error: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Sets Asterisk variables with VXML execution results.
     *
     * Allows dialplan to access results via:
     * - ${VXML_SESSION_ID}
     * - ${VXML_STATE}
     * - ${VXML_ERROR}
     * - ${VXML_RESULT_varname}
     *
     * @param channel AGI channel
     * @param vxmlSession VXML session with results
     */
    private void setAsteriskVariables(AgiChannel channel, VxmlSession vxmlSession) 
            throws AgiException {
        
        if (vxmlSession == null) {
            return;
        }

        try {
            // Set basic session info
            channel.setVariable("VXML_SESSION_ID", vxmlSession.getSessionId());
            channel.setVariable("VXML_STATE", vxmlSession.getState().toString());
            
            if (vxmlSession.getLastError() != null) {
                channel.setVariable("VXML_ERROR", vxmlSession.getLastError());
            }

            // Set collected variables
            for (Map.Entry<String, Object> entry : vxmlSession.getAllVariables().entrySet()) {
                String key = "VXML_RESULT_" + entry.getKey();
                String value = entry.getValue() != null ? entry.getValue().toString() : "";
                channel.setVariable(key, value);
                System.out.println("[VxmlAgiHandler] Set: " + key + " = " + value);
            }

            System.out.println("[VxmlAgiHandler] Asterisk variables set successfully");

        } catch (AgiException e) {
            System.err.println("[VxmlAgiHandler] Error setting Asterisk variables: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Handles post-execution logic (transfers, errors, etc.).
     *
     * @param channel AGI channel
     * @param vxmlSession VXML session
     */
    private void handlePostExecution(AgiChannel channel, VxmlSession vxmlSession) 
            throws AgiException {
        
        if (vxmlSession == null) {
            return;
        }

        // Handle different session states
        switch (vxmlSession.getState()) {
            case COMPLETED:
                System.out.println("[VxmlAgiHandler] VXML scenario completed successfully");
                channel.streamFile("demo-thanks");  // Play thank you message
                break;

            case ERROR:
                System.err.println("[VxmlAgiHandler] VXML scenario ended with error");
                channel.streamFile("vm-goodbye");   // Play goodbye
                break;

            case TIMEOUT:
                System.err.println("[VxmlAgiHandler] VXML scenario timed out");
                channel.streamFile("vm-goodbye");   // Play goodbye
                break;

            default:
                System.out.println("[VxmlAgiHandler] VXML scenario state: " + vxmlSession.getState());
        }
    }

    /**
     * Handles execution errors gracefully.
     *
     * @param channel AGI channel
     * @param sessionId session identifier
     * @param error exception that occurred
     */
    private void handleError(AgiChannel channel, String sessionId, Exception error) {
        try {
            System.err.println("[VxmlAgiHandler] Handling error for session: " + sessionId);
            System.err.println("[VxmlAgiHandler] Error: " + error.getMessage());
            error.printStackTrace();

            // Set error variables
            channel.setVariable("VXML_SESSION_ID", sessionId != null ? sessionId : "unknown");
            channel.setVariable("VXML_STATE", "ERROR");
            channel.setVariable("VXML_ERROR", error.getMessage());

            // Play error message
            try {
                channel.streamFile("vm-goodbye");
            } catch (AgiException e) {
                System.err.println("[VxmlAgiHandler] Could not play error message: " + e.getMessage());
            }
        } catch (Exception e) {
            System.err.println("[VxmlAgiHandler] Error handling failed: " + e.getMessage());
        }
    }

    /**
     * Shuts down the shared VXML engine.
     *
     * Call this when the application is shutting down.
     */
    public static void shutdownEngine() {
        synchronized (ENGINE_LOCK) {
            if (vxmlEngine != null) {
                try {
                    System.out.println("[VxmlAgiHandler] Shutting down VXML engine...");
                    vxmlEngine.shutdown();
                    vxmlEngine = null;
                    System.out.println("[VxmlAgiHandler] VXML engine shut down successfully");
                } catch (Exception e) {
                    System.err.println("[VxmlAgiHandler] Error shutting down engine: " + e.getMessage());
                }
            }
        }
    }
}
