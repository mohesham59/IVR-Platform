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
 * <li>Receives FastAGI call from Asterisk (e.g.,
 * agi://127.0.0.1:4573/hello)</li>
 * <li>Extracts VXML name from:
 * <ul>
 * <li>VXML_FILE Asterisk variable (if set)</li>
 * <li>AGI request path (e.g., "/hello" → "hello.vxml")</li>
 * <li>Default fallback: "hello.vxml"</li>
 * </ul>
 * </li>
 * <li>Loads the VXML using VxmlLoader</li>
 * <li>Executes it via VxmlScenarioEngine</li>
 * <li>Collects results (DTMF input, form data) back to Asterisk variables</li>
 * </ol>
 *
 * <h2>USAGE FROM ASTERISK</h2>
 * 
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
 * <li>VXML_SESSION_ID: Unique session identifier</li>
 * <li>VXML_STATE: Final session state (COMPLETED, ERROR, etc.)</li>
 * <li>VXML_ERROR: Error message (if any)</li>
 * <li>VXML_RESULT_*: Form results (e.g., VXML_RESULT_user_choice)</li>
 * </ul>
 *
 * <h2>EXAMPLE DIALPLAN USAGE</h2>
 * 
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

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(VxmlAgiHandler.class);

    // Shared VxmlScenarioEngine instance (initialized once)
    private static volatile VxmlScenarioEngine vxmlEngine;
    private static final Object ENGINE_LOCK = new Object();

    @Override
    public void service(AgiRequest request, AgiChannel channel) throws AgiException {
        String callerId = "unknown";
        String vxmlName = "hello"; // Default VXML
        String sessionId = null;

        try {
            // Extract caller information
            callerId = request.getCallerId() != null ? request.getCallerId() : request.getCallerIdName();
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
        String requestPath = request.getRequestURL(); // e.g., "agi://127.0.0.1:4573/hello"
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
        String script = request.getScript(); // May also contain path info
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
     * @param request  AGI request
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
     * @param request  AGI request
     * @param callerId caller identifier
     * @return ConnectionInformation for VXML execution
     */
    private ConnectionInformation createConnectionInfo(AgiRequest request, String callerId) {
        try {
            return new gov.iti.telecom.platform.AsteriskConnectionInformation(
                    "default", // profile
                    "asterisk-output", // system output
                    "asterisk-input", // user input
                    "asterisk-call-control", // call control
                    new URI("sip:" + callerId), // called device (caller)
                    new URI("sip:ivr@asterisk"), // calling device (IVR)
                    "SIP", // protocol
                    "2.0" // protocol version
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
     * Executes a VXML scenario and renders prompts & DTMF interactions
     * interactively to the channel.
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
                    renderMenuElement(menu, channel, session);
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

    private void renderDialogById(org.w3c.dom.Document doc, String dialogId, AgiChannel channel, VxmlSession session)
            throws Exception {
        org.w3c.dom.NodeList forms = doc.getElementsByTagName("form");
        for (int i = 0; i < forms.getLength(); i++) {
            org.w3c.dom.Element form = (org.w3c.dom.Element) forms.item(i);
            if (dialogId.equals(form.getAttribute("id"))) {
                renderFormElement(form, channel, session);
                return;
            }
        }

        org.w3c.dom.NodeList menus = doc.getElementsByTagName("menu");
        for (int i = 0; i < menus.getLength(); i++) {
            org.w3c.dom.Element menu = (org.w3c.dom.Element) menus.item(i);
            if (dialogId.equals(menu.getAttribute("id"))) {
                renderMenuElement(menu, channel, session);
                return;
            }
        }
    }

    private void renderMenuElement(org.w3c.dom.Element menu, AgiChannel channel, VxmlSession session) throws Exception {
        String maxRetriesStr = menu.getAttribute("max_retries");
        int maxRetries = 3;
        if (maxRetriesStr != null && !maxRetriesStr.isEmpty()) {
            try { maxRetries = Integer.parseInt(maxRetriesStr); } catch (Exception e) {}
        }
        String timeoutStr = menu.getAttribute("timeout");
        int timeoutMs = 10000;
        if (timeoutStr != null && !timeoutStr.isEmpty()) {
            if (timeoutStr.endsWith("s")) {
                try { timeoutMs = Integer.parseInt(timeoutStr.substring(0, timeoutStr.length() - 1)) * 1000; } catch (Exception e) {}
            }
        }

        int attempts = 0;
        while (attempts < maxRetries) {
            attempts++;
            char choice = 0;
            
            org.w3c.dom.NodeList children = menu.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    org.w3c.dom.Element child = (org.w3c.dom.Element) children.item(i);
                    if ("prompt".equals(child.getTagName())) {
                        choice = processPromptElementAndGetDigit(child, channel, session);
                        if (choice != 0 && choice != '\0') {
                            break;
                        }
                    }
                }
            }

            if (choice == 0 || choice == '\0') {
                choice = channel.waitForDigit(timeoutMs);
            }

            if (choice == 0 || choice == '\0') {
                System.out.println("[VxmlAgiHandler] DTMF choice timeout.");
                if (handleFallbackBlock(menu, "noinput", channel, session)) {
                    continue; // Reprompt requested
                }
                break; // Goto happened or no fallback block
            } else {
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
                    renderDialogById(menu.getOwnerDocument(), targetFormId, channel, session);
                    return;
                } else {
                    System.out.println("[VxmlAgiHandler] Invalid DTMF choice: " + choiceStr);
                    if (handleFallbackBlock(menu, "nomatch", channel, session)) {
                        continue; // Reprompt requested
                    }
                    break; // Goto happened or no fallback block
                }
            }
        }
    }

    private boolean handleFallbackBlock(org.w3c.dom.Element parent, String tagName, AgiChannel channel, VxmlSession session) throws Exception {
        org.w3c.dom.NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                org.w3c.dom.Element child = (org.w3c.dom.Element) children.item(i);
                if (tagName.equals(child.getTagName())) {
                    org.w3c.dom.NodeList fbChildren = child.getChildNodes();
                    boolean reprompt = false;
                    for (int j = 0; j < fbChildren.getLength(); j++) {
                        if (fbChildren.item(j).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                            org.w3c.dom.Element fbChild = (org.w3c.dom.Element) fbChildren.item(j);
                            if ("prompt".equals(fbChild.getTagName())) {
                                processPromptElement(fbChild, channel, session);
                            } else if ("goto".equals(fbChild.getTagName())) {
                                String next = fbChild.getAttribute("next");
                                if (next != null && next.startsWith("#")) {
                                    String targetFormId = next.substring(1);
                                    System.out.println("[VxmlAgiHandler] Fallback <goto> jumping to dialog: " + targetFormId);
                                    renderDialogById(parent.getOwnerDocument(), targetFormId, channel, session);
                                }
                                return false; // Goto executes, we don't reprompt
                            } else if ("reprompt".equals(fbChild.getTagName())) {
                                reprompt = true;
                            }
                        }
                    }
                    return reprompt;
                }
            }
        }
        return false;
    }

    private boolean renderFormElement(org.w3c.dom.Element form, AgiChannel channel, VxmlSession session) throws Exception {
        org.w3c.dom.NodeList children = form.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node node = children.item(i);
            if (node.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            org.w3c.dom.Element child = (org.w3c.dom.Element) node;
            String tagName = child.getTagName();

            if ("block".equals(tagName)) {
                if (renderFormElement(child, channel, session)) {
                    return true;
                }
            } else if ("prompt".equals(tagName)) {
                processPromptElement(child, channel, session);
            } else if ("goto".equals(tagName)) {
                String next = child.getAttribute("next");
                if (next != null && next.startsWith("#")) {
                    String targetFormId = next.substring(1);
                    System.out.println("[VxmlAgiHandler] <goto> jumping to dialog: " + targetFormId);
                    renderDialogById(form.getOwnerDocument(), targetFormId, channel, session);
                    return true;
                }
            } else if ("assign".equals(tagName)) {
                String name = child.getAttribute("name");
                String expr = child.getAttribute("expr");
                if (name != null && !name.isEmpty() && expr != null && session != null) {
                    if (expr.startsWith("'") && expr.endsWith("'")) {
                        expr = expr.substring(1, expr.length() - 1);
                    }
                    session.setVariable(name, expr);
                    System.out.println("[VxmlAgiHandler] <assign> " + name + " = " + expr);
                }
            } else if ("api".equals(tagName)) {
                String url = child.getAttribute("url");
                String varName = child.getAttribute("var");
                String saveResultAs = child.getAttribute("saveResultAs");
                String jsonPath = child.getAttribute("jsonPath"); // Optional: for extracting a specific field

                try {
                    String fullUrl = url;
                    if (varName != null && !varName.isEmpty() && session != null) {
                        Object val = session.getVariable(varName);
                        if (val != null) {
                            fullUrl += (url.contains("?") ? "&" : "?") + varName + "=" + val.toString();
                        }
                    }

                    System.out.println("[VxmlAgiHandler] <api> Calling URL: " + fullUrl);
                    java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                    java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                            .uri(URI.create(fullUrl))
                            .GET()
                            .build();
                    java.net.http.HttpResponse<String> response = client.send(req,
                            java.net.http.HttpResponse.BodyHandlers.ofString());
                    String responseBody = response.body();
                    System.out.println("[VxmlAgiHandler] <api> Response: " + responseBody);

                    String extractedResult = responseBody;
                    if (jsonPath != null && !jsonPath.isEmpty()) {
                        try {
                            com.google.gson.JsonObject jsonResponse = com.google.gson.JsonParser
                                    .parseString(responseBody).getAsJsonObject();
                            if (jsonResponse.has(jsonPath)) {
                                extractedResult = jsonResponse.get(jsonPath).getAsString();
                            }
                        } catch (Exception e) {
                            System.err.println("[VxmlAgiHandler] <api> Failed to parse JSON path: " + jsonPath);
                        }
                    }

                    if (saveResultAs != null && !saveResultAs.isEmpty() && session != null) {
                        session.setVariable(saveResultAs, extractedResult);
                        System.out.println("[VxmlAgiHandler] <api> Saved " + saveResultAs + " = " + extractedResult);
                    }
                } catch (Exception e) {
                    System.err.println("[VxmlAgiHandler] <api> request failed: " + e.getMessage());
                }
            } else if ("field".equals(tagName)) {
                String fieldName = child.getAttribute("name");
                String fieldType = child.getAttribute("type");
                org.w3c.dom.NodeList fieldPrompts = child.getElementsByTagName("prompt");

                // Parse <grammar> children for DTMF tokens
                java.util.List<String> grammarTokens = new java.util.ArrayList<>();
                org.w3c.dom.NodeList grammars = child.getElementsByTagName("grammar");
                for (int g = 0; g < grammars.getLength(); g++) {
                    org.w3c.dom.Element grammarEl = (org.w3c.dom.Element) grammars.item(g);
                    org.w3c.dom.NodeList items = grammarEl.getElementsByTagName("item");
                for (int j = 0; j < items.getLength(); j++) {
                    grammarTokens.add(items.item(j).getTextContent().trim());
                }
                    // Also check for inline text content
                    if (items.getLength() == 0) {
                        String grammarText = grammarEl.getTextContent().trim();
                        if (!grammarText.isEmpty()) {
                            grammarTokens.add(grammarText);
                        }
                    }
                }

                int maxRetries = 3;
                int timeoutMs = 10000;
                int attempts = 0;
                boolean matched = false;

                while (attempts < maxRetries && !matched) {
                    attempts++;
                    StringBuilder inputStr = new StringBuilder();
                    char firstDigit = 0;
                    
                    // Note: In standard VXML, prompts might only be played on the first attempt or reprompted.
                    // For simplicity, we play them each loop iteration like menu.
                    for (int fp = 0; fp < fieldPrompts.getLength(); fp++) {
                        firstDigit = processPromptElementAndGetDigit((org.w3c.dom.Element) fieldPrompts.item(fp), channel, session);
                        if (firstDigit != 0 && firstDigit != '\0') {
                            if (firstDigit != '#')
                                inputStr.append(firstDigit);
                            break;
                        }
                    }

                    if (firstDigit == 0 || firstDigit == '\0') {
                        firstDigit = channel.waitForDigit(timeoutMs);
                        if (firstDigit != 0 && firstDigit != '\0' && firstDigit != '#') {
                            inputStr.append(firstDigit);
                        }
                    }

                    if (inputStr.length() > 0 || firstDigit == '#') {
                        while (true) {
                            char nextDigit = channel.waitForDigit(5000); // 5 seconds timeout between digits
                            if (nextDigit == 0 || nextDigit == '\0' || nextDigit == '#') {
                                break;
                            }
                            inputStr.append(nextDigit);
                        }
                    }

                    if (inputStr.length() == 0) {
                        System.out.println("[VxmlAgiHandler] Field " + fieldName + " input timeout.");
                        if (handleFallbackBlock(child, "noinput", channel, session)) {
                            continue;
                        }
                        break;
                    } else {
                        // Check grammar if applicable (for simplicity we just accept it if there's no grammar enforcement)
                        boolean isValid = true;
                        if (!grammarTokens.isEmpty() && !grammarTokens.contains(inputStr.toString())) {
                            isValid = false;
                        }

                        if (isValid) {
                            String varKey = (fieldName != null && !fieldName.isEmpty()) ? fieldName : "user_input";
                            if (session != null) {
                                session.setVariable(varKey, inputStr.toString());
                            }
                            System.out.println("[VxmlAgiHandler] Field " + varKey + " input: " + inputStr.toString());
                            matched = true;
                            
                            // Handle <filled> block
                            org.w3c.dom.NodeList filledNodes = child.getElementsByTagName("filled");
                            if (filledNodes.getLength() > 0) {
                                org.w3c.dom.Element filled = (org.w3c.dom.Element) filledNodes.item(0);
                                renderFormElement(filled, channel, session);
                            }
                        } else {
                            System.out.println("[VxmlAgiHandler] Field " + fieldName + " invalid input: " + inputStr.toString());
                            if (handleFallbackBlock(child, "nomatch", channel, session)) {
                                continue;
                            }
                            break;
                        }
                    }
                }
            } else if ("ai".equals(tagName)) {
                String role = child.getAttribute("role");
                String options = child.getAttribute("options");

                String systemPrompt = role + " You must help the user choose one of these options: " + options + ". " +
                        "If the user's choice is clear, make a final decision immediately without asking for additional confirmation. If their choice is unclear, ask them to clarify. "
                        +
                        "Respond ONLY in valid JSON format: {\"status\": \"CONFIRMING\" or \"FINAL\", \"reply\": \"What you say to user\", \"action\": \"The exact destination ID (the part after the colon) if FINAL\"}";

                String conversationHistory = "";
                boolean isFinal = false;
                String finalAction = null;

                // 1. Play initial prompts inside <ai>
                org.w3c.dom.NodeList aiPrompts = child.getElementsByTagName("prompt");
                for (int p = 0; p < aiPrompts.getLength(); p++) {
                    String text = aiPrompts.item(p).getTextContent().trim();
                    processPromptElement((org.w3c.dom.Element) aiPrompts.item(p), channel, session);
                    if (!text.isEmpty()) {
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
                    String lang = resolveSessionLanguage(session);
                    String text = convertAudioToText(recordPath + ".wav", lang);
                    System.out.println("[VxmlAgiHandler] <ai> User said: " + text);

                    if (text == null || text.trim().isEmpty()) {
                        speakPrompt(channel, "I didn't hear anything. Let's try again.", session);
                        continue;
                    }

                    conversationHistory += "User: " + text + "\n";

                    // Get decision from Ollama
                    com.google.gson.JsonObject llmResponse = OllamaAgent.chatJson(systemPrompt, conversationHistory);
                    String status = llmResponse.has("status") ? llmResponse.get("status").getAsString() : "CONFIRMING";
                    String reply = llmResponse.has("reply") ? llmResponse.get("reply").getAsString() : "I am not sure.";

                    System.out.println("[VxmlAgiHandler] <ai> LLM Response: " + llmResponse.toString());

                    speakPrompt(channel, reply, session);
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
                    System.out.println("[VxmlAgiHandler] <ai> Jumping to dialog: " + finalAction);
                    renderDialogById(form.getOwnerDocument(), finalAction, channel, session);
                    return true;
                } else {
                    System.out.println("[VxmlAgiHandler] <ai> No final action, ending session.");
                    // You could optionally jump to a default form here.
                }
            } else if ("record".equals(tagName)) {
                String recordName = child.getAttribute("name");
                String maxTime = child.getAttribute("maxtime");
                String beep = child.getAttribute("beep");
                String dtmfterm = child.getAttribute("dtmfterm");
                String dest = child.getAttribute("dest");

                System.out.println("[VxmlAgiHandler] <record> Recording started: name=" + recordName
                        + " maxtime=" + maxTime + " beep=" + beep + " dtmfterm=" + dtmfterm);

                String recordPath = "/dev/shm/voicemail_" + System.currentTimeMillis();
                if (beep != null && beep.equalsIgnoreCase("true")) {
                    channel.streamFile("beep");
                }

                channel.recordFile(recordPath, "wav", "#",
                        maxTime != null && !maxTime.isEmpty() ? Integer.parseInt(maxTime.replaceAll("[^0-9]", "")) : 120,
                        0, false, 2000);

                if (session != null && recordName != null && !recordName.isEmpty()) {
                    session.setVariable(recordName, recordPath + ".wav");
                }

                if (dest != null && !dest.isEmpty()) {
                    System.out.println("[VxmlAgiHandler] <record> Saving voicemail to: " + dest);
                }

                speakPrompt(channel, "Your message has been recorded.", session);
            } else if ("var".equals(tagName)) {
                String varName = child.getAttribute("name");
                String expr = child.getAttribute("expr");
                if (varName != null && !varName.isEmpty() && expr != null && session != null) {
                    String value = expr;
                    if (value.startsWith("'") && value.endsWith("'")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    session.setVariable(varName, value);
                    System.out.println("[VxmlAgiHandler] <var> " + varName + " = " + value);
                }
            }
        }
        return false;
    }

    private String convertAudioToText(String wavFilePath, String langCode) throws Exception {
        String googleLang = "en-US";
        if (langCode != null && langCode.startsWith("ar")) {
            googleLang = "ar-EG";
        } else if (langCode != null && !langCode.trim().isEmpty() && !langCode.equals("en")) {
            googleLang = langCode;
        }

        String pythonScript = "import speech_recognition as sr\n" +
                "import sys\n" +
                "r = sr.Recognizer()\n" +
                "with sr.AudioFile(sys.argv[1]) as source:\n" +
                "    audio = r.record(source)\n" +
                "try:\n" +
                "    print(r.recognize_google(audio, language='" + googleLang + "'))\n" +
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
            System.err
                    .println("[VxmlAgiHandler] Python ASR failed with exit code " + exitCode + ". Output:\n" + output);
            return "";
        }

        return output;
    }

    private String substituteVariables(String text, VxmlSession session) {
        if (session == null || text == null || !text.contains("${"))
            return text;
        for (Map.Entry<String, Object> entry : session.getAllVariables().entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            if (text.contains(placeholder) && entry.getValue() != null) {
                text = text.replace(placeholder, entry.getValue().toString());
            }
        }
        return text;
    }

    private String resolveSessionLanguage(VxmlSession session) {
        if (session != null) {
            Object lang = session.getVariable("language");
            if (lang != null && !lang.toString().trim().isEmpty()) {
                return lang.toString();
            }
        }
        return VxmlConfig.loadFromClasspath().getTtsLanguage();
    }

    private char speakPromptAndGetDigit(AgiChannel channel, String text, VxmlSession session) {
        return speakPromptAndGetDigit(channel, text, session, resolveSessionLanguage(session));
    }

    private char speakPromptAndGetDigit(AgiChannel channel, String text, VxmlSession session, String lang) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        text = substituteVariables(text, session);
        try {
            System.out.println("[VxmlAgiHandler] Speaking prompt with DTMF listen: " + text);
            String streamPath = TtsEngine.getOrSynthesizeAudio(text, lang);
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

    private void speakPrompt(AgiChannel channel, String text, VxmlSession session) {
        speakPrompt(channel, text, session, resolveSessionLanguage(session));
    }

    private void speakPrompt(AgiChannel channel, String text, VxmlSession session, String lang) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        text = substituteVariables(text, session);
        try {
            System.out.println("[VxmlAgiHandler] Speaking prompt: " + text);
            TtsEngine.sayText(channel, text, lang);
        } catch (Exception e) {
            System.err.println("[VxmlAgiHandler] Audio playback exception: " + e.getMessage());
        }
    }

    /**
     * Processes a {@code <prompt>} element, handling both plain text and {@code <audio>} tags.
     *
     * <p>VXML usage:</p>
     * <pre>{@code
     * <prompt>
     *   <audio src="welcome.wav">Fallback TTS text if file not found</audio>
     *   Additional text rendered via TTS.
     * </prompt>
     * }</pre>
     *
     * <p>Audio files are resolved from {@code /var/lib/asterisk/sounds/ivr-custom/}.
     * If the audio file is not found, the inner text of the {@code <audio>} tag is
     * used as fallback for TTS synthesis.</p>
     *
     * @param promptElement the {@code <prompt>} DOM element
     * @param channel       Asterisk AGI channel for audio playback
     * @param session       current VXML session for variable substitution
     */
    private void processPromptElement(org.w3c.dom.Element promptElement, AgiChannel channel, VxmlSession session) {
        String promptLang = promptElement.getAttribute("xml:lang");
        if (promptLang == null || promptLang.trim().isEmpty()) {
            promptLang = resolveSessionLanguage(session);
        }

        org.w3c.dom.NodeList children = promptElement.getChildNodes();
        StringBuilder textBuffer = new StringBuilder();

        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);

            if (child.getNodeType() == org.w3c.dom.Node.TEXT_NODE) {
                textBuffer.append(child.getTextContent());
            } else if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                    && "audio".equals(child.getNodeName())) {
                // Flush any accumulated text before playing audio
                String accumulatedText = textBuffer.toString().trim();
                if (!accumulatedText.isEmpty()) {
                    speakPrompt(channel, accumulatedText, session, promptLang);
                    textBuffer.setLength(0);
                }

                org.w3c.dom.Element audioEl = (org.w3c.dom.Element) child;
                String audioLang = audioEl.getAttribute("xml:lang");
                if (audioLang == null || audioLang.trim().isEmpty()) {
                    audioLang = promptLang;
                }

                String src = audioEl.getAttribute("src");
                String streamPath = TtsEngine.resolveAudioSrc(src);

                if (streamPath != null) {
                    try {
                        System.out.println("[VxmlAgiHandler] Playing custom audio: " + streamPath);
                        channel.streamFile(streamPath);
                    } catch (Exception e) {
                        System.err.println("[VxmlAgiHandler] Error playing custom audio '" + src + "': " + e.getMessage());
                    }
                } else {
                    // Fallback: use inner text content for TTS
                    String fallbackText = audioEl.getTextContent().trim();
                    if (!fallbackText.isEmpty()) {
                        System.out.println("[VxmlAgiHandler] Audio file not found, using TTS fallback for: " + src);
                        speakPrompt(channel, fallbackText, session, audioLang);
                    } else {
                        System.err.println("[VxmlAgiHandler] <audio src=\"" + src + "\"> not found and no fallback text");
                    }
                }
            }
        }

        // Flush remaining text
        String remainingText = textBuffer.toString().trim();
        if (!remainingText.isEmpty()) {
            speakPrompt(channel, remainingText, session, promptLang);
        }
    }

    /**
     * Processes a {@code <prompt>} element with DTMF listening,
     * handling both plain text and {@code <audio>} tags.
     *
     * <p>Returns the first DTMF digit pressed during any segment of the prompt.
     * Each segment (text or audio) is played with DTMF interruptibility.</p>
     *
     * @param promptElement the {@code <prompt>} DOM element
     * @param channel       Asterisk AGI channel for audio playback
     * @param session       current VXML session for variable substitution
     * @return the DTMF digit pressed, or 0 if no digit was pressed
     */
    private char processPromptElementAndGetDigit(org.w3c.dom.Element promptElement, AgiChannel channel, VxmlSession session) {
        String promptLang = promptElement.getAttribute("xml:lang");
        if (promptLang == null || promptLang.trim().isEmpty()) {
            promptLang = resolveSessionLanguage(session);
        }

        org.w3c.dom.NodeList children = promptElement.getChildNodes();
        StringBuilder textBuffer = new StringBuilder();

        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);

            if (child.getNodeType() == org.w3c.dom.Node.TEXT_NODE) {
                textBuffer.append(child.getTextContent());
            } else if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                    && "audio".equals(child.getNodeName())) {
                // Flush any accumulated text before playing audio
                String accumulatedText = textBuffer.toString().trim();
                if (!accumulatedText.isEmpty()) {
                    char digit = speakPromptAndGetDigit(channel, accumulatedText, session, promptLang);
                    if (digit != 0 && digit != '\0') return digit;
                    textBuffer.setLength(0);
                }

                org.w3c.dom.Element audioEl = (org.w3c.dom.Element) child;
                String audioLang = audioEl.getAttribute("xml:lang");
                if (audioLang == null || audioLang.trim().isEmpty()) {
                    audioLang = promptLang;
                }

                String src = audioEl.getAttribute("src");
                String streamPath = TtsEngine.resolveAudioSrc(src);

                if (streamPath != null) {
                    try {
                        System.out.println("[VxmlAgiHandler] Playing custom audio (DTMF-aware): " + streamPath);
                        char digit = channel.streamFile(streamPath, "0123456789*#");
                        if (digit != 0 && digit != '\0') return digit;
                    } catch (Exception e) {
                        System.err.println("[VxmlAgiHandler] Error playing custom audio '" + src + "': " + e.getMessage());
                    }
                } else {
                    // Fallback: use inner text content for TTS
                    String fallbackText = audioEl.getTextContent().trim();
                    if (!fallbackText.isEmpty()) {
                        System.out.println("[VxmlAgiHandler] Audio file not found, using TTS fallback for: " + src);
                        char digit = speakPromptAndGetDigit(channel, fallbackText, session, audioLang);
                        if (digit != 0 && digit != '\0') return digit;
                    } else {
                        System.err.println("[VxmlAgiHandler] <audio src=\"" + src + "\"> not found and no fallback text");
                    }
                }
            }
        }

        // Flush remaining text
        String remainingText = textBuffer.toString().trim();
        if (!remainingText.isEmpty()) {
            return speakPromptAndGetDigit(channel, remainingText, session, promptLang);
        }

        return 0;
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
     * @param channel   AGI channel
     * @param sessionId session identifier
     * @param error     exception that occurred
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
