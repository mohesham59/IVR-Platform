package com.nexusivr.ai.dto.common;

/**
 * Distinguishes a one-shot summary of a single finished session (SESSION)
 * from a summary that folds a new session into a customer's existing
 * cross-session memory (ROLLING). Both land in conversation_history; this
 * only changes how the Summarization module builds its prompt/context.
 */
public enum SummaryType {
    SESSION,
    ROLLING
}
