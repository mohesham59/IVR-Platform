package com.nexusivr.ai.model;

/**
 * Closed set of values for {@code prompt_templates.module}.
 * Extend this list as new modules (e.g. FLOWGENERATOR, SENTIMENT) ship
 * in later versions of the schema.
 */
public enum PromptModule {
    ASSISTANT,
    RAG,
    SUMMARY
}
