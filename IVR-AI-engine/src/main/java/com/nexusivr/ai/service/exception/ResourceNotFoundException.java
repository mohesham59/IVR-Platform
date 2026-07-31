package com.nexusivr.ai.service.exception;

/**
 * Thrown when a request references an id (sessionId, conversation
 * history id, etc.) that the DAO layer could not locate.
 */
public class ResourceNotFoundException extends ServiceException {

    public ResourceNotFoundException(String resourceType, String identifier) {
        super("RESOURCE_NOT_FOUND", resourceType + " not found for id: " + identifier);
    }
}
