package com.nexusivr.ai.service;

/**
 * Functional interface for receiving real-time progress callbacks during AI pipeline operations.
 */
@FunctionalInterface
public interface ProgressListener {
    /**
     * Callback invoked when the AI pipeline transitions to a new stage.
     *
     * @param stage   the stage key (e.g. "understanding", "analysis", "planning", "template", "generating", "validating", "converting", "rendering")
     * @param message human-readable status description for logging or UI display
     */
    void onProgress(String stage, String message);
}
