package com.nexusivr.ai.service.exception;

import java.util.Collections;
import java.util.List;

/**
 * Thrown when an inbound request DTO fails structural/shape validation
 * (required field missing, malformed id, invalid enum combination,
 * out-of-range paging, etc.) before any DAO or Provider is invoked.
 * Never represents a business-rule failure — those belong to whatever
 * Provider or DAO implementation eventually enforces them.
 */
public class ValidationException extends ServiceException {

    private final List<String> violations;

    public ValidationException(List<String> violations) {
        super("VALIDATION_ERROR", "Request failed validation: " + violations);
        this.violations = violations == null ? Collections.emptyList() : violations;
    }

    public List<String> getViolations() {
        return violations;
    }
}
