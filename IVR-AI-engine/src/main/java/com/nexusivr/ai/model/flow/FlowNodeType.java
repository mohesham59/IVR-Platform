package com.nexusivr.ai.model.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Supported node types in the Internal Flow Model.
 * Each type maps to a VoiceXML element and a Builder node type.
 */
public enum FlowNodeType {
    START("start", "Entry point of the call"),
    PROMPT("prompt", "Text-to-speech or audio playback"),
    MENU("menu", "DTMF menu with choices"),
    INPUT("input", "Collect DTMF input"),
    TRANSFER("transfer", "Transfer to agent or number"),
    QUEUE("queue", "Call queue"),
    CONDITION("condition", "Conditional branching"),
    BUSINESS_HOURS("business_hours", "Business hours check"),
    HOLIDAY("holiday", "Holiday check"),
    RECORDING("recording", "Record caller audio"),
    API("api", "HTTP API call"),
    DATABASE("database", "Database lookup"),
    VOICEMAIL("voicemail", "Voicemail collection"),
    WEBHOOK("webhook", "Webhook trigger"),
    AI("ai", "AI bot agent"),
    END("end", "Terminate call"),
    DISCONNECT("disconnect", "Hang up");

    private final String builderType;
    private final String description;

    FlowNodeType(String builderType, String description) {
        this.builderType = builderType;
        this.description = description;
    }

    public String getBuilderType() {
        return builderType;
    }

    public String getDescription() {
        return description;
    }

    public static FlowNodeType fromString(String value) {
        if (value == null) return null;
        return switch (value.toLowerCase()) {
            case "start" -> START;
            case "prompt", "playback", "tts" -> PROMPT;
            case "menu", "dtmf_menu" -> MENU;
            case "input", "field", "dtmf_input" -> INPUT;
            case "transfer" -> TRANSFER;
            case "queue" -> QUEUE;
            case "condition", "if" -> CONDITION;
            case "business_hours", "hours" -> BUSINESS_HOURS;
            case "holiday" -> HOLIDAY;
            case "recording", "record" -> RECORDING;
            case "api" -> API;
            case "database" -> DATABASE;
            case "voicemail" -> VOICEMAIL;
            case "webhook" -> WEBHOOK;
            case "ai" -> AI;
            case "end", "hangup", "disconnect" -> END;
            default -> null;
        };
    }

    public static FlowNodeType fromVoiceXmlTag(String tag) {
        if (tag == null) return null;
        return switch (tag.toLowerCase()) {
            case "form" -> null; // form is a container, type determined by children
            case "block" -> PROMPT;
            case "menu" -> MENU;
            case "field" -> INPUT;
            case "transfer" -> TRANSFER;
            case "queue" -> QUEUE;
            case "if" -> CONDITION;
            case "disconnect", "hangup" -> END;
            default -> null;
        };
    }
}
