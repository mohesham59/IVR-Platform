package com.nexusivr.ai.dto.common;

/**
 * Coarse-grained sentiment classification returned by the Sentiment module
 * and reused inside Analytics rollups. Kept separate from any DB-backed
 * enum (there is no sentiment table in the MVP schema) because this value
 * is computed at request time, never persisted behind a CHECK constraint.
 */
public enum SentimentLabel {
    POSITIVE,
    NEUTRAL,
    NEGATIVE,
    MIXED
}
