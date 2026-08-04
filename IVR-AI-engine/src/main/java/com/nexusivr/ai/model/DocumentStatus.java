package com.nexusivr.ai.model;

/**
 * Closed set of values for {@code knowledge_documents.status}.
 * Tracks a document's position in the RAG ingestion pipeline.
 */
public enum DocumentStatus {
    PENDING,
    INGESTING,
    INGESTED,
    FAILED
}
