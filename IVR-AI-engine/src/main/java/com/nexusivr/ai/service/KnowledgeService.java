package com.nexusivr.ai.service;

import com.nexusivr.ai.dao.KnowledgeDocumentDao;
import com.nexusivr.ai.exception.DataAccessException;
import com.nexusivr.ai.exception.ResourceNotFoundException;
import com.nexusivr.ai.exception.ServiceException;
import com.nexusivr.ai.exception.ValidationException;
import com.nexusivr.ai.model.DocumentStatus;
import com.nexusivr.ai.model.KnowledgeDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Concrete service managing RAG Knowledge Base documents.
 * Handles document upload, status transitions, and chunking pipeline preparation.
 */
public class KnowledgeService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeService.class);

    private final KnowledgeDocumentDao documentDao;

    public KnowledgeService(KnowledgeDocumentDao documentDao) {
        this.documentDao = Objects.requireNonNull(documentDao, "documentDao must not be null");
    }

    public KnowledgeDocument uploadDocument(KnowledgeDocument document) {
        if (document == null || document.getTenantId() == null) {
            throw new ValidationException("Document and tenantId are required");
        }
        if (document.getTitle() == null || document.getTitle().trim().isEmpty()) {
            throw new ValidationException("Document title is required");
        }

        document.setStatus(DocumentStatus.PENDING);
        try {
            KnowledgeDocument created = documentDao.create(document);
            logger.info("KnowledgeDocument uploaded successfully: {} for tenant {}", created.getId(), created.getTenantId());
            
            // Integration hook for RAG ingestion pipeline: PENDING -> INGESTING -> INGESTED
            processDocumentIngestionAsync(created.getId(), created.getTenantId());
            return created;
        } catch (DataAccessException e) {
            logger.error("Error uploading KnowledgeDocument for tenant {}", document.getTenantId(), e);
            throw new ServiceException("Unable to upload document", e);
        }
    }

    public List<KnowledgeDocument> getDocuments(UUID tenantId) {
        if (tenantId == null) {
            throw new ValidationException("tenantId is required");
        }
        try {
            return documentDao.findAll(tenantId);
        } catch (DataAccessException e) {
            logger.error("Error retrieving KnowledgeDocuments for tenant {}", tenantId, e);
            throw new ServiceException("Error retrieving documents", e);
        }
    }

    public boolean updateDocumentStatus(UUID id, UUID tenantId, DocumentStatus status) {
        if (id == null || tenantId == null || status == null) {
            throw new ValidationException("id, tenantId, and status are required");
        }
        try {
            boolean updated = documentDao.updateStatus(id, tenantId, status);
            if (!updated) {
                throw new ResourceNotFoundException("KnowledgeDocument not found for ID: " + id);
            }
            return true;
        } catch (DataAccessException e) {
            logger.error("Error updating Document status for id {} tenant {}", id, tenantId, e);
            throw new ServiceException("Error updating document status", e);
        }
    }

    public boolean deleteDocument(UUID id, UUID tenantId) {
        if (id == null || tenantId == null) {
            throw new ValidationException("id and tenantId are required");
        }
        try {
            boolean deleted = documentDao.delete(id, tenantId);
            if (!deleted) {
                throw new ResourceNotFoundException("KnowledgeDocument not found for ID: " + id);
            }
            return true;
        } catch (DataAccessException e) {
            logger.error("Error deleting KnowledgeDocument id {} tenant {}", id, tenantId, e);
            throw new ServiceException("Error deleting document", e);
        }
    }

    private void processDocumentIngestionAsync(UUID documentId, UUID tenantId) {
        // Placeholder method for future asynchronous RAG pipeline processing
        logger.debug("Triggering asynchronous RAG chunking & embedding pipeline for document: {}", documentId);
    }
}
