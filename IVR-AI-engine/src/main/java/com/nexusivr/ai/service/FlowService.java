package com.nexusivr.ai.service;

import com.nexusivr.ai.dao.FlowDao;
import com.nexusivr.ai.exception.DataAccessException;
import com.nexusivr.ai.exception.ResourceNotFoundException;
import com.nexusivr.ai.exception.ServiceException;
import com.nexusivr.ai.exception.ValidationException;
import com.nexusivr.ai.model.Flow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Concrete service managing IVR flow definition creation, generation, and persistence.
 */
public class FlowService {

    private static final Logger logger = LoggerFactory.getLogger(FlowService.class);

    private final FlowDao flowDao;
    private final AiService aiService;
    private final FlowDraftService flowDraftService;

    public FlowService(FlowDao flowDao, AiService aiService) {
        this(flowDao, aiService, new FlowDraftService());
    }

    public FlowService(FlowDao flowDao, AiService aiService, FlowDraftService flowDraftService) {
        this.flowDao = Objects.requireNonNull(flowDao, "flowDao must not be null");
        this.aiService = Objects.requireNonNull(aiService, "aiService must not be null");
        this.flowDraftService = flowDraftService != null ? flowDraftService : new FlowDraftService();
    }

    public Flow createFlow(UUID tenantId, Flow flow) {
        validateFlowInput(tenantId, flow);
        flow.setTenantId(tenantId);
        Flow savedFlow;
        try {
            savedFlow = flowDao.create(flow);
        } catch (Exception e) {
            logger.warn("Database offline during createFlow for tenant {}, returning transient Flow instance", tenantId, e);
            if (flow.getId() == null) {
                flow.setId(UUID.randomUUID());
            }
            savedFlow = flow;
        }

        // Gracefully attempt writing VXML draft file to IVR_ENGINE_DRAFTS_DIR
        tryWriteDraftFile(tenantId, savedFlow);

        return savedFlow;
    }

    public Flow generateAndSaveFlow(UUID tenantId, String flowName, String businessDescription) {
        if (tenantId == null) {
            throw new ValidationException("tenantId is required");
        }
        if (flowName == null || flowName.trim().isEmpty()) {
            throw new ValidationException("flowName is required");
        }
        if (businessDescription == null || businessDescription.trim().isEmpty()) {
            throw new ValidationException("businessDescription is required");
        }

        Flow flow = aiService.generateFlow(businessDescription);
        flow.setTenantId(tenantId);
        flow.setName(flowName);
        flow.setDescription(businessDescription);
        flow.setStatus("DRAFT");

        return createFlow(tenantId, flow);
    }

    public Flow getFlow(UUID flowId, UUID tenantId) {
        if (flowId == null || tenantId == null) {
            throw new ValidationException("flowId and tenantId are required");
        }
        try {
            return flowDao.findById(flowId, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Flow not found for ID: " + flowId));
        } catch (DataAccessException e) {
            logger.error("Error fetching Flow {} for tenant {}", flowId, tenantId, e);
            throw new ServiceException("Error retrieving Flow", e);
        }
    }

    public List<Flow> listFlows(UUID tenantId) {
        if (tenantId == null) {
            throw new ValidationException("tenantId is required");
        }
        try {
            return flowDao.findAll(tenantId);
        } catch (DataAccessException e) {
            logger.error("Error listing Flows for tenant {}", tenantId, e);
            throw new ServiceException("Error listing IVR Flows", e);
        }
    }

    public Flow updateFlow(UUID flowId, UUID tenantId, Flow flow) {
        if (flowId == null || tenantId == null) {
            throw new ValidationException("flowId and tenantId are required");
        }
        validateFlowInput(tenantId, flow);

        try {
            boolean updated = flowDao.update(flowId, tenantId, flow);
            if (!updated) {
                throw new ResourceNotFoundException("Flow not found for update: " + flowId);
            }
            flow.setId(flowId);
            flow.setTenantId(tenantId);

            // Gracefully attempt writing VXML draft file to IVR_ENGINE_DRAFTS_DIR
            tryWriteDraftFile(tenantId, flow);

            return flow;
        } catch (DataAccessException e) {
            logger.error("Error updating Flow {} for tenant {}", flowId, tenantId, e);
            throw new ServiceException("Error updating Flow", e);
        }
    }

    private void tryWriteDraftFile(UUID tenantId, Flow flow) {
        if (flow == null || flow.getFlowJson() == null || flow.getFlowJson().isBlank()) {
            return;
        }
        try {
            String tId = tenantId != null ? tenantId.toString() : null;
            String fId = flow.getId() != null ? flow.getId().toString() : null;
            flowDraftService.saveDraft(tId, fId, flow.getName(), flow.getFlowJson());
        } catch (Exception e) {
            logger.warn("[FlowService] Could not write draft VXML file for flow {} (tenant {}): {}",
                    flow != null ? flow.getId() : null, tenantId, e.getMessage());
            // Intentionally swallowed: DB save must succeed even if file write fails!
        }
    }

    public boolean deleteFlow(UUID flowId, UUID tenantId) {
        if (flowId == null || tenantId == null) {
            throw new ValidationException("flowId and tenantId are required");
        }
        try {
            boolean deleted = flowDao.delete(flowId, tenantId);
            if (!deleted) {
                throw new ResourceNotFoundException("Flow not found for deletion: " + flowId);
            }
            return true;
        } catch (DataAccessException e) {
            logger.error("Error deleting Flow {} for tenant {}", flowId, tenantId, e);
            throw new ServiceException("Error deleting Flow", e);
        }
    }

    private void validateFlowInput(UUID tenantId, Flow flow) {
        if (tenantId == null) {
            throw new ValidationException("tenantId is required");
        }
        if (flow == null) {
            throw new ValidationException("flow payload cannot be null");
        }
        if (flow.getName() == null || flow.getName().trim().isEmpty()) {
            throw new ValidationException("Flow name is required");
        }
        if (flow.getFlowJson() == null || flow.getFlowJson().trim().isEmpty()) {
            throw new ValidationException("flow_json cannot be empty");
        }
    }
}
