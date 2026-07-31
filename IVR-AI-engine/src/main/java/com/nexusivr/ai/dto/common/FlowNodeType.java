package com.nexusivr.ai.dto.common;

/**
 * The kind of step a node in an IVR/chat flow represents. Used by
 * Flow Generator, Flow Improvement, Validation, and Router DTOs, all of
 * which operate on the same in-memory flow graph shape (FlowDto).
 * There is no flow_generations table in the MVP schema (it is explicitly
 * deferred to v2 per the database design doc), so this enum exists only
 * at the API/DTO layer today — it does not mirror a CHECK constraint the
 * way the model-package enums (Channel, SessionStatus, etc.) do.
 */
public enum FlowNodeType {
    MENU,
    PROMPT,
    COLLECT_INPUT,
    CONDITION,
    FUNCTION_CALL,
    TRANSFER,
    HANGUP
}
