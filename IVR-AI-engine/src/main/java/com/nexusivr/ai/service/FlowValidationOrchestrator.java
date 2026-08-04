package com.nexusivr.ai.service;

import com.nexusivr.ai.dto.response.FlowValidationResponse;
import com.nexusivr.ai.model.flow.FlowModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deterministic validation-repair loop.
 * <p>
 * Repeatedly validates a FlowModel, repairs it, and revalidates until:
 * <ul>
 *   <li>The model is valid, OR</li>
 *   <li>The maximum number of iterations is reached.</li>
 * </ul>
 * <p>
 * Never uses AI. All operations are deterministic.
 * </p>
 */
public class FlowValidationOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(FlowValidationOrchestrator.class);
    private static final int DEFAULT_MAX_ITERATIONS = 10;

    private final FlowModelValidator validator;
    private final FlowModelAutoRepair repairer;
    private final int maxIterations;

    public FlowValidationOrchestrator() {
        this(new FlowModelValidator(), new FlowModelAutoRepair(), DEFAULT_MAX_ITERATIONS);
    }

    public FlowValidationOrchestrator(FlowModelValidator validator, FlowModelAutoRepair repairer, int maxIterations) {
        this.validator = validator;
        this.repairer = repairer;
        this.maxIterations = maxIterations;
    }

    public FlowValidationOrchestratorResult validateAndRepair(FlowModel model) {
        if (model == null || model.getNodes().isEmpty()) {
            return new FlowValidationOrchestratorResult(
                    validator.validate(model),
                    0,
                    false,
                    "Empty or null model"
            );
        }

        FlowValidationResponse currentValidation = validator.validate(model);
        int iterations = 0;
        int lastScore = -1;
        int lastIssueCount = -1;
        boolean stalled = false;

        while (!currentValidation.isValid() && iterations < maxIterations) {
            int score = currentValidation.getScore();
            int issueCount = currentValidation.getIssues().size();

            if (iterations > 0 && score == lastScore && issueCount == lastIssueCount) {
                logger.warn("[FlowValidationOrchestrator] Repair stalled — no progress after {} iteration(s). Score: {}, Issues: {}. Stopping early.",
                        iterations, score, issueCount);
                stalled = true;
                break;
            }

            lastScore = score;
            lastIssueCount = issueCount;

            logger.info("[FlowValidationOrchestrator] Iteration {}: repairing {} issues",
                    iterations + 1, currentValidation.getIssues().size());

            FlowModel repaired = repairer.repair(model);

            currentValidation = validator.validate(repaired);
            iterations++;

            if (currentValidation.isValid()) {
                logger.info("[FlowValidationOrchestrator] Model became valid after {} iteration(s). Score: {}",
                        iterations, currentValidation.getScore());
                return new FlowValidationOrchestratorResult(
                        currentValidation,
                        iterations,
                        true,
                        "Valid after " + iterations + " iteration(s)"
                );
            }

            model = repaired;
        }

        String status;
        boolean converged;
        if (currentValidation.isValid()) {
            status = "Valid after " + iterations + " iteration(s)";
            converged = true;
        } else if (stalled) {
            status = "Repair stalled — no progress after " + iterations + " attempt(s). Score: " + currentValidation.getScore();
            converged = false;
        } else if (iterations >= maxIterations) {
            status = "Max iterations (" + maxIterations + ") reached. Still invalid. Score: " + currentValidation.getScore();
            converged = false;
        } else {
            status = "Stopped early. Score: " + currentValidation.getScore();
            converged = false;
        }

        logger.info("[FlowValidationOrchestrator] Complete. Iterations={}, Converged={}, Score={}, Issues={}",
                iterations, converged, currentValidation.getScore(), currentValidation.getIssues().size());

        return new FlowValidationOrchestratorResult(currentValidation, iterations, converged, status);
    }

    public static class FlowValidationOrchestratorResult {
        private final FlowValidationResponse finalValidation;
        private final int iterations;
        private final boolean converged;
        private final String message;

        public FlowValidationOrchestratorResult(FlowValidationResponse finalValidation, int iterations, boolean converged, String message) {
            this.finalValidation = finalValidation;
            this.iterations = iterations;
            this.converged = converged;
            this.message = message;
        }

        public FlowValidationResponse getFinalValidation() { return finalValidation; }
        public int getIterations() { return iterations; }
        public boolean isConverged() { return converged; }
        public String getMessage() { return message; }
    }
}
