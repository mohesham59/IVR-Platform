package com.nexusivr.ai.service;

import com.nexusivr.ai.dao.KnowledgeDocumentDao;
import com.nexusivr.ai.exception.ValidationException;
import com.nexusivr.ai.model.KnowledgeDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("KnowledgeService Unit Tests")
public class KnowledgeServiceTest {

    private KnowledgeDocumentDao documentDao;
    private KnowledgeService knowledgeService;

    @BeforeEach
    void setUp() {
        documentDao = new KnowledgeDocumentDao();
        knowledgeService = new KnowledgeService(documentDao);
    }

    @Test
    @DisplayName("Should throw ValidationException if document title is missing")
    void testUploadDocumentValidation() {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setTenantId(UUID.randomUUID());

        assertThrows(ValidationException.class, () -> knowledgeService.uploadDocument(doc));
    }
}
