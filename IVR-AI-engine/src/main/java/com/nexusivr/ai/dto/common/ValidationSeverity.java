package com.nexusivr.ai.dto.common;

/**
 * Severity of a single finding returned by flow validation. ERROR blocks
 * publishing a flow; WARNING and INFO are advisory only. Ordered from
 * most to least severe for callers that want to sort findings for display.
 */
public enum ValidationSeverity {
    ERROR,
    WARNING,
    INFO
}
