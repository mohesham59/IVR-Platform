package com.nexusivr.ai.dto.common;

/**
 * Selects whether a Sentiment request should score a single ad-hoc piece
 * of text (MESSAGE) or roll up sentiment across an entire session's
 * transcript (SESSION, read from ai_messages via sessionId).
 */
public enum AnalysisScope {
    MESSAGE,
    SESSION
}
