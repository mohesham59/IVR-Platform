package com.nexusivr.ai.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of supported node types and allowed output ports.
 */
public class NodeLibrary {
    public static final Map<String, String> SUPPORTED_TYPES = new LinkedHashMap<>();

    static {
        SUPPORTED_TYPES.put("start", "Entry point. Output port: [out]. Title should be descriptive, e.g. 'Incoming Call' or 'Customer Contact'.");
        SUPPORTED_TYPES.put("greeting", "Play welcome message. Output port: [out]. Title should be descriptive, e.g. 'Welcome Greeting' or 'Main Menu Welcome'.");
        SUPPORTED_TYPES.put("playback", "Play audio file. Output port: [out]. Title should describe the audio, e.g. 'Play Welcome Message' or 'Play Hold Music'.");
        SUPPORTED_TYPES.put("tts", "Text-to-speech message. Output port: [out]. Title should describe what is spoken, e.g. 'Account Balance Announcement'.");
        SUPPORTED_TYPES.put("dtmf_menu", "Multi-option menu. Output ports: [key1, key2, key3, key4, key5, key6, key7, key8, key9, key0, timeout]. Title should describe the menu purpose, e.g. 'Banking Services Menu' or 'Department Selection Menu'.");
        SUPPORTED_TYPES.put("dtmf_input", "Collect digits. Output ports: [success, timeout]. Title should describe what is collected, e.g. 'Collect Account Number' or 'Collect PIN'.");
        SUPPORTED_TYPES.put("queue", "Call Queue. Output ports: [answered, abandoned, overflow]. Title should describe the queue purpose, e.g. 'Customer Support Queue' or 'Billing Queue'.");
        SUPPORTED_TYPES.put("transfer", "Live transfer to phone/SIP. Output ports: [success, fail]. Title should describe the transfer destination, e.g. 'Transfer to Banking Representative' or 'Transfer to Billing Department'.");
        SUPPORTED_TYPES.put("extension", "Dial extension. Output ports: [answered, noanswer]. Title should describe the extension purpose, e.g. 'Dial Sales Extension'.");
        SUPPORTED_TYPES.put("voicemail", "Record voicemail. Output port: [done]. Title should be 'Voicemail' or 'After-Hours Voicemail'.");
        SUPPORTED_TYPES.put("record", "Record conversation. Output port: [out]. Title should describe what is recorded, e.g. 'Record Customer Name'.");
        SUPPORTED_TYPES.put("api", "HTTP API Request. Output ports: [success, error]. Title should describe the API purpose, e.g. 'Fetch Account Balance' or 'Check Order Status'.");
        SUPPORTED_TYPES.put("database", "Database lookup. Output ports: [found, notfound]. Title should describe the lookup, e.g. 'Lookup Customer Record' or 'Verify Account Number'.");
        SUPPORTED_TYPES.put("hours", "Business hours check. Output ports: [open, closed]. Title should be 'Business Hours Check' or 'Operating Hours Check'.");
        SUPPORTED_TYPES.put("holiday", "Holiday check. Output ports: [holiday, normal]. Title should be 'Holiday Check' or 'Check Holiday Calendar'.");
        SUPPORTED_TYPES.put("condition", "Conditional branch. Output ports: [true, false]. Title should describe the condition, e.g. 'Check Balance Sufficient' or 'Is After Hours'.");
        SUPPORTED_TYPES.put("variable", "Set flow variable. Output port: [out]. Title should describe what is set, e.g. 'Set Caller ID Variable'.");
        SUPPORTED_TYPES.put("webhook", "Trigger outbound webhook. Output ports: [success, error]. Title should describe the webhook purpose, e.g. 'Notify CRM System'.");
        SUPPORTED_TYPES.put("ai", "Conversational AI bot agent. Output ports: [resolved, escalate]. Title should describe the AI purpose, e.g. 'AI Support Assistant' or 'Virtual Receptionist'.");
        SUPPORTED_TYPES.put("end", "Terminate call. No output ports (terminal node). Title should be 'End Call' or 'Hang Up'.");
    }

    public static String getLibraryString() {
        StringBuilder sb = new StringBuilder();
        SUPPORTED_TYPES.forEach((type, desc) -> sb.append("- ").append(type).append(": ").append(desc).append("\n"));
        return sb.toString();
    }
}
