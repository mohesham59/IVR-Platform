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
        - BILINGUAL REQUIREMENT: ALWAYS generate TWO <prompt> elements for EVERY message: one in English `<prompt xml:lang="en">` and one translated into Arabic `<prompt xml:lang="ar">`. Alternatively, use `<en>` and `<ar>` child tags inside a single `<prompt>`. This applies to blocks, menus, fields, and anywhere text is spoken.
        - START NODE: The very first form MUST have `id="start"`. This start node MUST contain a `<block>` with a single `<goto>` pointing to a Language Selection Menu.
        - LANGUAGE SELECTION MENU: The language selection menu must ask the caller to press 1 for English and 2 for Arabic (e.g. "For English press 1, للعربية اضغط 2"). The choices must `<goto>` two separate assignment forms. The English form must contain an `<assign name="language" expr="'en'"/>` inside a `<block>`. The Arabic form must contain an `<assign name="language" expr="'ar'"/>` inside a `<block>`. After setting the variable, both forms MUST converge by using `<goto>` to the exact same main menu form. NEVER use `<var>` tags to set this variable; use EXACTLY `<assign name="language" expr="'en'"/>` (and `'ar'` for Arabic).
        - FALLBACK PROMPTS: NEVER include error messages (e.g., "We did not receive any input", "Invalid input", "Try again") in the main `<prompt>` text of a menu or field. This is STRICTLY FORBIDDEN. Instead, generate separate `<noinput>` and `<nomatch>` elements inside the `<menu>`.

        WRONG (choice tags inside block — INVALID VoiceXML):
        <form id="menu">
          <block>
            <prompt xml:lang="en">Press 1 for Sales, Press 2 for Support.</prompt>
            <prompt xml:lang="ar">اضغط 1 للمبيعات، اضغط 2 للدعم.</prompt>
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
              <prompt xml:lang="en">Welcome to our service. Press 1 for sales, Press 2 for support.</prompt>
              <prompt xml:lang="ar">مرحبا بك في خدمتنا. اضغط 1 للمبيعات، 2 للدعم.</prompt>
              <goto next="#menu"/>
            </block>
          </form>
          <form id="menu">
            <menu>
              <prompt xml:lang="en">Please select an option.</prompt>
              <prompt xml:lang="ar">الرجاء تحديد خيار.</prompt>
              <choice accept="digits 1" next="#sales"/>
              <choice accept="digits 2" next="#support"/>
            </menu>
          </form>
          <form id="sales">
            <block>
              <prompt xml:lang="en">Connecting you to sales.</prompt>
              <prompt xml:lang="ar">جاري تحويلك للمبيعات.</prompt>
              <transfer dest="+1001"/>
            </block>
          </form>
          <form id="support">
            <block>
              <prompt xml:lang="en">Connecting you to support.</prompt>
              <prompt xml:lang="ar">جاري تحويلك للدعم.</prompt>
              <disconnect/>
            </block>
          </form>
        </vxml>

        DTMF FIELD EXAMPLE:
        <form id="authenticate">
          <field name="account">
            <prompt xml:lang="en">Enter your 4 digit PIN followed by pound.</prompt>
            <prompt xml:lang="ar">أدخل الرمز السري المكون من 4 أرقام متبوعا بمربع.</prompt>
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
              <prompt xml:lang="en">We did not receive any input. Please try again.</prompt>
              <prompt xml:lang="ar">لم نتلق أي إدخال. يرجى المحاولة مرة أخرى.</prompt>
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

        IF / ELSEIF / ELSE EXAMPLE (CORRECT NESTING):
        <form id="route">
          <block>
            <if cond="day == 'weekday'">
              <goto next="#business_hours"/>
            </if>
            <elseif cond="day == 'saturday'">
              <goto next="#saturday_hours"/>
            </elseif>
            <else>
              <goto next="#closed"/>
            </else>
          </block>
        </form>

        ESCAPED ENTITIES EXAMPLE:
        <prompt>Billing &amp; Payments &amp;gt; Option 1 &amp;gt; Option 2</prompt>

        RULES:
        - Keep prompts concise (1-2 sentences).
        - Minimize forms: use the fewest forms necessary.
        - Every form reachable from #start.
        - Every leaf path ends with <transfer dest="..."/> or <disconnect/>.
        - SINGLE DISCONNECT POINT RULE: All call-terminating paths (hang-ups, goodbyes, post-transaction closings) must <goto> a single shared closing/end form rather than each containing their own <disconnect/>. Only one form in the entire flow should contain <disconnect/>.
        - <transfer dest="..."> RULES:
          1. NEVER use free-text human/role/department names (like "Billing Dept" or "Front Desk") as the transfer destination.
          2. Use dialable extensions or SIP URIs.
          3. If the destination is unknown, use "TRANSFER_TARGET_PLACEHOLDER".
        - Close every opened tag.
        - Any literal '&' in prompt text MUST be escaped as '&amp;'. Never output a bare '&' inside text content.
        - AI DYNAMIC ROUTING & FALLBACK MENU WIRING RULE: When generating a dynamic routing AI node, specify a fallback path via a nomatch handler that routes back to the main menu using: <goto next="#main_menu"/>.
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
