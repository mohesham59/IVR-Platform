package com.nexusivr.ai.model;

/**
 * Closed set of values for {@code ai_sessions.channel}.
 * Mirrors the CHECK constraint on the column; kept as a typed enum
 * (rather than a raw String) so invalid channels are caught at compile
 * time wherever possible, not just at the database layer.
 */
public enum Channel {
    VOICE,
    CHAT,
    WHATSAPP,
    WEB_WIDGET,
    SMS
}
