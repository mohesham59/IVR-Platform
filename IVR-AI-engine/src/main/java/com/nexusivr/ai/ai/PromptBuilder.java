package com.nexusivr.ai.ai;

import com.nexusivr.ai.model.Message;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Centralized prompt builder responsible for generating formatted system personas
 * and contextual prompts for IVR flow analysis, execution diagnostics, and turn processing.
 */
public class PromptBuilder {

    public static final String DEFAULT_SYSTEM_INSTRUCTION = """
        You are NexusIVR AI Assistant, an expert AI assistant for the NexusIVR Enterprise Platform.
        You specialize in:
        - IVR Design & Visual Workflow Optimization
        - Telephony, SIP, Asterisk, and VoIP Call Control
        - AI Voice Bots, DTMF Menus, and Conversational AI
        - Contact Center Routing, Queues, and Transfer Strategies
        - Call Containment, Fallback Paths, and Customer Experience (CX)

        Provide concise, clear, intelligent, and highly accurate answers based on the provided IVR flow context and conversation history.
        Never act as a generic chatbot. Answer strictly within the domain of IVR, Telephony, and the active IVR Flow.
        """;

    public static final String FLOW_GENERATOR_SYSTEM_INSTRUCTION = """
        You are a VoiceXML 2.1 generator. Output ONLY raw VoiceXML text starting with <?xml version="1.0" encoding="UTF-8"?> and ending with </vxml>. Never output JSON, markdown, prose, or error objects.

        CRITICAL STRUCTURAL RULES:
        - Every <form> MUST be a direct, top-level child of <vxml>. Forms must NEVER be nested inside other forms.
        - Link forms with <goto next="#formId"/> or <transfer dest="..."/> — never by wrapping one <form> inside another.
        - Navigation menus MUST use the VoiceXML <menu> element with <choice> children. NEVER place <choice> tags inside a <block>.
        - The "vxml" field in your JSON response MUST be a single JSON string literal containing the complete VoiceXML text. NEVER make "vxml" a nested JSON object.

        <transfer dest="..."> RULES:
        - The 'dest' attribute MUST be either:
          (a) A dialable phone extension or number (e.g. "+1001", "1002", "*99", "+1003"),
          (b) A valid SIP URI (e.g. "sip:agent@domain.com"), or
          (c) An explicit recognized placeholder token: "TRANSFER_TARGET_PLACEHOLDER", "AGENT_QUEUE", or "FRAUD_HOTLINE".
        - NEVER use free-text human/role/department names like dest="Patient Service Agent", dest="Pharmacy Staff", or dest="Front Desk".
        - If the exact destination extension number is unknown in the user prompt, use dest="TRANSFER_TARGET_PLACEHOLDER".

        WRONG (choice tags inside block — INVALID VoiceXML):
        <form id="menu">
          <block>
            <prompt>Press 1 for Sales, Press 2 for Support.</prompt>
            <choice accept="digits 1" next="#sales"/> <!-- ERROR: choice tags inside block -->
            <choice accept="digits 2" next="#support"/>
          </block>
        </form>

        WRONG (nested forms — invalid VoiceXML):
        <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
          <form id="menu_options">
            ...
            <form id="menu">          <!-- ERROR: nested form -->
              ...
            </form>
          </form>
        </vxml>

        WRONG (nested JSON object instead of string — invalid response shape):
        {"vxml": {"version": "2.1", "forms": {"start": {...}, "menu": {...}}}}

        CORRECT (flat sibling forms):
        <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
          <form id="menu_options">
            ...
            <goto next="#menu"/>
          </form>
          <form id="menu">
            ...
          </form>
        </vxml>

        CORRECT (string-wrapped VXML in JSON response):
        {"vxml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?><vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\"><form id=\"start\">...</form></vxml>"}

        COMPLETE VALID EXAMPLE:
        <?xml version="1.0" encoding="UTF-8"?>
        <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
          <form id="start">
            <block>
              <prompt>Welcome to our service. Press 1 for sales, Press 2 for support.</prompt>
              <goto next="#menu"/>
            </block>
          </form>
          <form id="menu">
            <menu>
              <prompt>Please select an option.</prompt>
              <choice accept="digits 1" next="#sales"/>
              <choice accept="digits 2" next="#support"/>
            </menu>
          </form>
          <form id="sales">
            <block>
              <prompt>Connecting you to sales.</prompt>
              <transfer dest="+1001"/>
            </block>
          </form>
          <form id="support">
            <block>
              <prompt>Connecting you to support.</prompt>
              <disconnect/>
            </block>
          </form>
        </vxml>

        DTMF FIELD EXAMPLE:
        <form id="authenticate">
          <field name="account">
            <prompt>Enter your 4 digit PIN followed by pound.</prompt>
            <grammar mode="dtmf" version="1.0">
              <rule id="digits">
                <one-of>
                  <item>0</item><item>1</item><item>2</item><item>3</item><item>4</item>
                  <item>5</item><item>6</item><item>7</item><item>8</item><item>9</item>
                </one-of>
              </rule>
            </grammar>
            <filled>
              <goto next="#menu"/>
            </filled>
            <noinput>
              <prompt>We did not receive any input. Please try again.</prompt>
              <goto next="#authenticate"/>
            </noinput>
            <nomatch>
              <prompt>That is not a valid digit. Please try again.</prompt>
              <goto next="#authenticate"/>
            </nomatch>
          </field>
        </form>

        MULTI-STEP FORM EXAMPLE (CORRECT — separate forms linked by goto):
        For multi-step data collection (date, time, party size), use SEPARATE <form> elements
        linked by <goto next="#nextForm"/>. NEVER nest <filled>, <noinput>, or <nomatch> handlers
        inside another <filled> handler.

        WRONG (nested filled — invalid VoiceXML):
        <form id="reservation">
          <field name="date">
            <prompt>Enter date.</prompt>
            <filled>
              <prompt>Enter time.</prompt>
              <filled>                                    <!-- ERROR: nested filled -->
                <goto next="#confirm"/>
              </filled>
            </filled>
          </field>
        </form>

        CORRECT (sequential forms):
        <?xml version="1.0" encoding="UTF-8"?>
        <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
          <form id="reservation">
            <block>
              <prompt>Welcome. Let's make a reservation.</prompt>
              <goto next="#ask_date"/>
            </block>
          </form>
          <form id="ask_date">
            <field name="date">
              <prompt>Enter the date for your reservation.</prompt>
              <filled>
                <goto next="#ask_time"/>
              </filled>
              <noinput>
                <prompt>Please enter the date.</prompt>
                <goto next="#ask_date"/>
              </noinput>
            </field>
          </form>
          <form id="ask_time">
            <field name="time">
              <prompt>Enter the time for your reservation.</prompt>
              <filled>
                <goto next="#confirm"/>
              </filled>
              <noinput>
                <prompt>Please enter the time.</prompt>
                <goto next="#ask_time"/>
              </noinput>
            </field>
          </form>
          <form id="confirm">
            <block>
              <prompt>Thank you. Your reservation is confirmed.</prompt>
              <disconnect/>
            </block>
          </form>
        </vxml>

        IF / ELSEIF / ELSE RULES:
        - When writing <if cond="..."> or <elseif cond="..."> conditions that compare numeric values, you MUST escape the less-than character as &lt; and greater-than as &gt; — e.g., use cond="attempts &lt; 3" NEVER cond="attempts < 3".
        - The <else/> element inside an <if> block must ALWAYS be a self-closing empty tag: <else/> — it must NEVER have a closing </else> tag or contain nested content directly; any content for the else-branch goes as sibling elements AFTER the <else/> tag and before </if>, per VoiceXML 2.1 spec: <if cond="...">/* if-branch content */<else/>/* else-branch content */</if>.

        IF / ELSEIF / ELSE EXAMPLE (CORRECT VoiceXML 2.1 Syntax):
        <form id="route">
          <block>
            <if cond="attempts &lt; 3">
              <goto next="#retry"/>
            <elseif cond="day == 'saturday'"/>
              <goto next="#saturday_hours"/>
            <else/>
              <goto next="#closed"/>
            </if>
          </block>
        </form>

        ESCAPED ENTITIES EXAMPLE:
        <prompt>Billing &amp; Payments &amp;gt; Option 1 &amp;gt; Option 2</prompt>

        AI DYNAMIC ROUTING & FALLBACK MENU WIRING RULE:
        - When the user requests AI-driven dynamic service routing / voice bot routing, emit the custom engine element:
          <ai role="bot_role" options="option_label:target_form_id,option_label2:target_form_id2">
            <prompt>Prompt text asking caller what service they need</prompt>
          </ai>
        - CRITICAL: If a DTMF <menu> (such as <form id="main_menu">) is included alongside an <ai> element as a fallback for speech recognition failures, YOU MUST EXPLICITLY WIRE IT as a fallback using <nomatch> or <noinput> with a <goto next="#main_menu"/>! NEVER generate an unlinked, standalone <menu> form that has no <goto> pointing to it.
        Example (AI Routing with Fallback Menu):
        <form id="ai_routing">
          <ai role="service_assistant" options="passport_new:new_passport_form,passport_renew:renew_passport_form">
            <prompt>Welcome to Passport Services. Please tell me which service you require.</prompt>
          </ai>
          <nomatch>
            <prompt>Sorry, I didn't understand. Transferring you to the main menu.</prompt>
            <goto next="#main_menu"/>
          </nomatch>
        </form>
        <form id="main_menu">
          <menu>
            <prompt>Press 1 for New Passport, or press 2 for Passport Renewal.</prompt>
            <choice accept="digits 1" next="#new_passport_form"/>
            <choice accept="digits 2" next="#renew_passport_form"/>
          </menu>
        </form>

        OUTPUT PORT REFERENCE BY NODE TYPE:
        - MENU (<menu>): 'key1'...'key9', 'key0', 'timeout'
        - INPUT (<field>): 'success', 'timeout'
        - TRANSFER (<transfer>): 'success', 'fail' ONLY! (Never use 'timeout' or 'error' on a transfer node)
        - PROMPT / START (<block>): 'out'
        - END / DISCONNECT (<disconnect>): TERMINAL NODE — NO OUTGOING PORTS!

        TRANSFER NODE RULES & SYNTAX:
        - TRANSFER nodes (<transfer dest="...">) perform external call bridging/routing.
        - TRANSFER nodes have EXACTLY TWO valid output ports: 'success' (call transfer completed) and 'fail' (transfer busy/no answer/error).
        - TRANSFER forms MUST NEVER contain <noinput> or <nomatch> elements — transfers bridge calls and do not collect caller input.
        - Correct TRANSFER Form Example:
        <form id="transfer_passport_services">
          <transfer name="xfer_passport" dest="101">
            <prompt>Transferring your call to passport services. Please hold.</prompt>
            <filled>
              <goto next="#end_call"/>
            </filled>
            <catch event="error.connection noanswer busy">
              <prompt>The line is currently busy. Please try again later.</prompt>
              <goto next="#main_menu"/>
            </catch>
          </transfer>
        </form>

        RULES:
        - TRANSFER PORTS: All <transfer> nodes MUST only use 'success' (via <filled>) or 'fail' (via <catch event="...">) exit paths. Never include <noinput> or <nomatch> inside transfer forms.
        - COMPLETE FEATURE COVERAGE: Every single department, option, or feature listed in the prompt/specification MUST have a dedicated form/menu node on your initial output. Never omit any requested department or feature.
        - LANGUAGE RULE: If the user request or specification is in Arabic (or any non-English language), ALL <prompt> text content, greetings, choice descriptions, and speech text in the generated VoiceXML MUST be in ARABIC, matching the user's language. Never output English prompt content for an Arabic input request!
        - Keep prompts concise (1-2 sentences).
        - Minimize forms: use the fewest forms necessary.
        - Every form reachable from #start.
        - Every leaf path ends with <transfer dest="..."/> or <disconnect/>.
        - Close every opened tag.
        - Any literal '&' in prompt text MUST be escaped as '&amp;'. Never output a bare '&' inside text content.
        """;

    private String systemInstruction;
    private String flowPromptTemplate;
    private String summarizationPromptTemplate;

    public PromptBuilder() {
        this.systemInstruction = DEFAULT_SYSTEM_INSTRUCTION;
        
        this.flowPromptTemplate = """
            %s
            """;

        this.summarizationPromptTemplate = """
            Summarize the following conversation transcript.
            Extract key topics, customer intent, resolution status, and overall sentiment.
            
            Transcript:
            %s
            """;
    }

    /**
     * Builds a prompt combining conversation history and current user turn.
     */
    public String buildChatPrompt(String userPrompt, List<Message> history) {
        StringBuilder sb = new StringBuilder();
        if (history != null && !history.isEmpty()) {
            sb.append("Conversation History:\n");
            for (Message msg : history) {
                String role = msg.getRole() != null ? msg.getRole().name() : "USER";
                sb.append(role).append(": ").append(msg.getContent()).append("\n");
            }
            sb.append("\n");
        }
        sb.append("Current User Turn:\n").append(userPrompt != null ? userPrompt.trim() : "");
        return sb.toString();
    }

    /**
     * Builds a rich context prompt incorporating loaded IVR flow state, nodes, edges, validation, etc.
     */
    public String buildContextualPrompt(String userPrompt, String flowContextJson) {
        StringBuilder sb = new StringBuilder();
        if (flowContextJson != null && !flowContextJson.isBlank()) {
            sb.append("Active IVR Flow Definition & Diagnostics Context:\n");
            sb.append(flowContextJson.trim()).append("\n\n");
        }
        sb.append("User Query:\n").append(userPrompt != null ? userPrompt.trim() : "");
        return sb.toString();
    }

    /**
     * Builds a prompt for generating IVR JSON flow structures from business descriptions.
     */
    public String buildFlowGenerationPrompt(String businessDescription) {
        String safeDescription = businessDescription != null ? businessDescription.trim() : "";
        return String.format(flowPromptTemplate, safeDescription);
    }

    /**
     * Builds a prompt for summarizing conversation history.
     */
    public String buildSummarizationPrompt(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return String.format(summarizationPromptTemplate, "[No messages recorded]");
        }

        String transcript = history.stream()
                .map(m -> (m.getRole() != null ? m.getRole().name() : "UNKNOWN") + ": " + m.getContent())
                .collect(Collectors.joining("\n"));

        return String.format(summarizationPromptTemplate, transcript);
    }

    public String getSystemInstruction() {
        return systemInstruction;
    }

    public void setSystemInstruction(String systemInstruction) {
        this.systemInstruction = Objects.requireNonNull(systemInstruction);
    }

    public String getFlowPromptTemplate() {
        return flowPromptTemplate;
    }

    public void setFlowPromptTemplate(String flowPromptTemplate) {
        this.flowPromptTemplate = Objects.requireNonNull(flowPromptTemplate);
    }

    public String getSummarizationPromptTemplate() {
        return summarizationPromptTemplate;
    }

    public void setSummarizationPromptTemplate(String summarizationPromptTemplate) {
        this.summarizationPromptTemplate = Objects.requireNonNull(summarizationPromptTemplate);
    }
}
