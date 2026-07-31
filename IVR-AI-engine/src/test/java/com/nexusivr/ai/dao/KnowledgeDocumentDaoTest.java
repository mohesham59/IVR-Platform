package com.nexusivr.ai.dao;

import com.nexusivr.ai.model.DocumentStatus;
import com.nexusivr.ai.model.KnowledgeDocument;
import com.nexusivr.ai.model.SourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("KnowledgeDocumentDao Integration Preparation Test")
public class KnowledgeDocumentDaoTest {

    @Test
    @DisplayName("Should construct KnowledgeDocument model cleanly")
    void testKnowledgeDocumentConstruction() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(id);
        doc.setTenantId(tenantId);
        doc.setTitle("IVR Support Guide v1");
        doc.setSourceType(SourceType.UPLOAD);
        doc.setStatus(DocumentStatus.PENDING);
        doc.setVersion(1);

        assertEquals(id, doc.getId());
        assertEquals(tenantId, doc.getTenantId());
        assertEquals("IVR Support Guide v1", doc.getTitle());
        assertEquals(DocumentStatus.PENDING, doc.getStatus());
    }
}
