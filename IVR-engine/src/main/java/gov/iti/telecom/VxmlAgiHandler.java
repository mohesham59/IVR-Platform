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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
            VxmlSession vxmlSession = executeVxmlScenario(vxmlName, connInfo, sessionId, channel);

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
     * Executes a VXML scenario and renders prompts & DTMF interactions interactively to the channel.
     */
    private VxmlSession executeVxmlScenario(String vxmlName, 
                                            ConnectionInformation connInfo,
                                            String sessionId,
                                            AgiChannel channel) throws Exception {
        try {
            System.out.println("[VxmlAgiHandler] Starting VXML execution: " + vxmlName);
            VxmlSession session = vxmlEngine.executeVxml(vxmlName, connInfo);
            
            if (session != null) {
                session.setVariable("agi_session_id", sessionId);
            }

            // Render VXML Document prompts & DTMF input interactively
            try {
                VxmlLoader loader = new VxmlLoader("scenarios/");
                org.w3c.dom.Document doc = loader.loadVxml(vxmlName);

                org.w3c.dom.NodeList menus = doc.getElementsByTagName("menu");
                org.w3c.dom.NodeList forms = doc.getElementsByTagName("form");

                if (menus.getLength() > 0) {
                    org.w3c.dom.Element menu = (org.w3c.dom.Element) menus.item(0);
                    org.w3c.dom.NodeList prompts = menu.getElementsByTagName("prompt");
                    char choice = 0;
                    for (int p = 0; p < prompts.getLength(); p++) {
                        String promptText = prompts.item(p).getTextContent().trim();
                        if (!promptText.isEmpty()) {
                            choice = speakPromptAndGetDigit(channel, promptText);
                            if (choice != 0 && choice != '\0') {
                                break;
                            }
                        }
                    }
                    
                    if (choice == 0 || choice == '\0') {
                        choice = channel.waitForDigit(10000);
                    }

                    if (choice != 0 && choice != '\0') {
                        String choiceStr = String.valueOf(choice);
                        if (session != null) {
                            session.setVariable("user_choice", choiceStr);
                        }
                        System.out.println("[VxmlAgiHandler] DTMF choice received: " + choiceStr);

                        org.w3c.dom.NodeList choices = menu.getElementsByTagName("choice");
                        String targetFormId = null;
                        for (int c = 0; c < choices.getLength(); c++) {
                            org.w3c.dom.Element ch = (org.w3c.dom.Element) choices.item(c);
                            if (choiceStr.equals(ch.getAttribute("dtmf"))) {
                                String next = ch.getAttribute("next");
                                if (next != null && next.startsWith("#")) {
                                    targetFormId = next.substring(1);
                                }
                                break;
                            }
                        }

                        if (targetFormId != null) {
                            renderFormById(doc, targetFormId, channel, session);
                        }
                    }
                } else if (forms.getLength() > 0) {
                    org.w3c.dom.Element initialForm = (org.w3c.dom.Element) forms.item(0);
                    renderFormElement(initialForm, channel, session);
                }
            } catch (Exception e) {
                System.out.println("[VxmlAgiHandler] Note rendering VXML: " + e.getMessage());
            }
            
            return session;
        } catch (Exception e) {
            System.err.println("[VxmlAgiHandler] VXML execution error: " + e.getMessage());
            throw e;
        }
    }

    private void renderFormById(org.w3c.dom.Document doc, String formId, AgiChannel channel, VxmlSession session) throws Exception {
        org.w3c.dom.NodeList forms = doc.getElementsByTagName("form");
        for (int i = 0; i < forms.getLength(); i++) {
            org.w3c.dom.Element form = (org.w3c.dom.Element) forms.item(i);
            if (formId.equals(form.getAttribute("id"))) {
                renderFormElement(form, channel, session);
                break;
            }
        }
    }

    private void renderFormElement(org.w3c.dom.Element form, AgiChannel channel, VxmlSession session) throws Exception {
        org.w3c.dom.NodeList blocks = form.getElementsByTagName("block");
        for (int b = 0; b < blocks.getLength(); b++) {
            org.w3c.dom.Element block = (org.w3c.dom.Element) blocks.item(b);
            org.w3c.dom.NodeList prompts = block.getElementsByTagName("prompt");
            for (int p = 0; p < prompts.getLength(); p++) {
                String text = prompts.item(p).getTextContent().trim();
                if (!text.isEmpty()) {
                    speakPrompt(channel, text);
                }
            }
        }

        org.w3c.dom.NodeList fields = form.getElementsByTagName("field");
        for (int f = 0; f < fields.getLength(); f++) {
            org.w3c.dom.Element field = (org.w3c.dom.Element) fields.item(f);
            String fieldName = field.getAttribute("name");
            org.w3c.dom.NodeList fieldPrompts = field.getElementsByTagName("prompt");
            char digit = 0;
            for (int fp = 0; fp < fieldPrompts.getLength(); fp++) {
                String promptText = fieldPrompts.item(fp).getTextContent().trim();
                if (!promptText.isEmpty()) {
                    digit = speakPromptAndGetDigit(channel, promptText);
                    if (digit != 0 && digit != '\0') {
                        break;
                    }
                }
            }
            if (digit == 0 || digit == '\0') {
                digit = channel.waitForDigit(10000);
            }
            if (digit != 0 && digit != '\0') {
                String varKey = (fieldName != null && !fieldName.isEmpty()) ? fieldName : "user_input";
                if (session != null) {
                    session.setVariable(varKey, String.valueOf(digit));
                }
                System.out.println("[VxmlAgiHandler] Field " + varKey + " input: " + digit);
            }
        }

        // Handle <ai> tags
        org.w3c.dom.NodeList aiNodes = form.getElementsByTagName("ai");
        for (int a = 0; a < aiNodes.getLength(); a++) {
            org.w3c.dom.Element aiNode = (org.w3c.dom.Element) aiNodes.item(a);
            String role = aiNode.getAttribute("role");
            String options = aiNode.getAttribute("options");
            
            String systemPrompt = role + " You must help the user choose one of these options: " + options + ". " +
                "You must ask for confirmation before making a final decision. " +
                "Respond ONLY in valid JSON format: {\"status\": \"CONFIRMING\" or \"FINAL\", \"reply\": \"What you say to user\", \"action\": \"The exact destination ID (the part after the colon) if FINAL\"}";
                
            String conversationHistory = "";
            boolean isFinal = false;
            String finalAction = null;
            
            // 1. Play initial prompts inside <ai>
            org.w3c.dom.NodeList aiPrompts = aiNode.getElementsByTagName("prompt");
            for (int p = 0; p < aiPrompts.getLength(); p++) {
                String text = aiPrompts.item(p).getTextContent().trim();
                if (!text.isEmpty()) {
                    speakPrompt(channel, text);
                    conversationHistory += "AI: " + text + "\n";
                }
            }
            
            while (!isFinal) {
                // Beep before recording
                channel.streamFile("beep");
                
                // Record audio in /dev/shm to bypass systemd PrivateTmp isolation
                String recordPath = "/dev/shm/ai_audio_" + System.currentTimeMillis();
                channel.recordFile(recordPath, "wav", "#", 5000, 0, false, 2000);
                
                // Convert to text
                String text = convertAudioToText(recordPath + ".wav");
                System.out.println("[VxmlAgiHandler] <ai> User said: " + text);
                
                if (text == null || text.trim().isEmpty()) {
                    speakPrompt(channel, "I didn't hear anything. Let's try again.");
                    continue;
                }
                
                conversationHistory += "User: " + text + "\n";
                
                // Get decision from Ollama
                com.google.gson.JsonObject llmResponse = OllamaAgent.chatJson(systemPrompt, conversationHistory);
                String status = llmResponse.has("status") ? llmResponse.get("status").getAsString() : "CONFIRMING";
                String reply = llmResponse.has("reply") ? llmResponse.get("reply").getAsString() : "I am not sure.";
                
                System.out.println("[VxmlAgiHandler] <ai> LLM Response: " + llmResponse.toString());
                
                speakPrompt(channel, reply);
                conversationHistory += "AI: " + reply + "\n";
                
                if ("FINAL".equalsIgnoreCase(status)) {
                    isFinal = true;
                    finalAction = llmResponse.has("action") ? llmResponse.get("action").getAsString() : null;
                }
            }
            
            if (finalAction != null && !finalAction.trim().isEmpty()) {
                if (finalAction.contains(":")) {
                    finalAction = finalAction.split(":")[1].trim();
                }
                System.out.println("[VxmlAgiHandler] <ai> Jumping to form: " + finalAction);
                renderFormById(form.getOwnerDocument(), finalAction, channel, session);
            } else {
                System.out.println("[VxmlAgiHandler] <ai> No final action, ending session.");
                // You could optionally jump to a default form here.
            }
        }
    }

    private String convertAudioToText(String wavFilePath) throws Exception {
        String pythonScript = "import speech_recognition as sr\n" +
                "import sys\n" +
                "r = sr.Recognizer()\n" +
                "with sr.AudioFile(sys.argv[1]) as source:\n" +
                "    audio = r.record(source)\n" +
                "try:\n" +
                "    print(r.recognize_google(audio))\n" +
                "except Exception as e:\n" +
                "    import traceback\n" +
                "    traceback.print_exc(file=sys.stderr)\n" +
                "    sys.exit(1)\n";

        Path scriptPath = Paths.get("/dev/shm/asr.py");
        Files.writeString(scriptPath, pythonScript);

        ProcessBuilder pb = new ProcessBuilder("python3", "/dev/shm/asr.py", wavFilePath);
        pb.redirectErrorStream(true); // capture stderr with stdout
        Process p = pb.start();
        
        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line).append("\n");
        }
        int exitCode = p.waitFor();
        String output = sb.toString().trim();
        
        if (exitCode != 0) {
            System.err.println("[VxmlAgiHandler] Python ASR failed with exit code " + exitCode + ". Output:\n" + output);
            return "";
        }
        
        return output;
    }

    private char speakPromptAndGetDigit(AgiChannel channel, String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        try {
            System.out.println("[VxmlAgiHandler] Speaking prompt with DTMF listen: " + text);
            String streamPath = TtsEngine.getOrSynthesizeAudio(text);
            if (streamPath != null) {
                char digit = channel.streamFile(streamPath, "0123456789*#");
                if (digit != 0 && digit != '\0') {
                    System.out.println("[VxmlAgiHandler] DTMF keypress during audio playback: " + digit);
                    return digit;
                }
            }
        } catch (Exception e) {
            System.err.println("[VxmlAgiHandler] Audio playback exception: " + e.getMessage());
        }
        return 0;
    }

    private void speakPrompt(AgiChannel channel, String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        try {
            System.out.println("[VxmlAgiHandler] Speaking prompt: " + text);
            TtsEngine.sayText(channel, text);
        } catch (Exception e) {
            System.err.println("[VxmlAgiHandler] Audio playback exception: " + e.getMessage());
        }
    }

    /**
     * Sets Asterisk variables with VXML execution results.
     */
    private void setAsteriskVariables(AgiChannel channel, VxmlSession vxmlSession) 
            throws AgiException {
        
        if (vxmlSession == null) {
            return;
        }

        try {
            channel.setVariable("VXML_SESSION_ID", vxmlSession.getSessionId());
            channel.setVariable("VXML_STATE", vxmlSession.getState().toString());
            
            if (vxmlSession.getLastError() != null) {
                channel.setVariable("VXML_ERROR", vxmlSession.getLastError());
            }

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
     * Handles post-execution logic.
     */
    private void handlePostExecution(AgiChannel channel, VxmlSession vxmlSession) 
            throws AgiException {
        if (vxmlSession == null) {
            return;
        }

        switch (vxmlSession.getState()) {
            case COMPLETED:
                System.out.println("[VxmlAgiHandler] VXML scenario completed successfully");
                break;

            case ERROR:
                System.err.println("[VxmlAgiHandler] VXML scenario ended with error: " + vxmlSession.getLastError());
                break;

            case TIMEOUT:
                System.err.println("[VxmlAgiHandler] VXML scenario timed out");
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
