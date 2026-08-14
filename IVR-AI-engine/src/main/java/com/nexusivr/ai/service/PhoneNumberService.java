package com.nexusivr.ai.service;

import com.nexusivr.ai.config.LlmConfig;
import com.nexusivr.ai.dao.PhoneNumberDao;
import com.nexusivr.ai.exception.ServiceException;
import com.nexusivr.ai.exception.ValidationException;
import com.nexusivr.ai.model.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class PhoneNumberService {

    private static final Logger logger = LoggerFactory.getLogger(PhoneNumberService.class);
    private final PhoneNumberDao phoneNumberDao;
    private final FlowPublishService flowPublishService;

    public PhoneNumberService(PhoneNumberDao phoneNumberDao, FlowPublishService flowPublishService) {
        this.phoneNumberDao = phoneNumberDao;
        this.flowPublishService = flowPublishService;
    }

    public PhoneNumberService() {
        this(new PhoneNumberDao(), new FlowPublishService());
    }

    public List<PhoneNumber> getPhoneNumbers(UUID tenantId) {
        if (tenantId == null) {
            return Collections.emptyList();
        }
        return phoneNumberDao.findByTenantId(tenantId);
    }

    public Map<String, Object> getPhoneNumberStats(UUID tenantId) {
        if (tenantId == null) {
            return Map.of("totalNumbers", 0, "activeNumbers", 0, "unassignedNumbers", 0, "todaysInbound", 0);
        }
        List<PhoneNumber> list = phoneNumberDao.findByTenantId(tenantId);
        int total = list.size();
        int active = (int) list.stream().filter(p -> "ACTIVE".equalsIgnoreCase(p.getStatus())).count();
        int unassigned = (int) list.stream().filter(p -> "UNASSIGNED".equalsIgnoreCase(p.getStatus())).count();
        int todaysInbound = phoneNumberDao.getTodaysInboundCallsCount(tenantId);

        Map<String, Object> map = new HashMap<>();
        map.put("totalNumbers", total);
        map.put("activeNumbers", active);
        map.put("unassignedNumbers", unassigned);
        map.put("todaysInbound", todaysInbound);
        return map;
    }

    public PhoneNumber addPhoneNumber(UUID tenantId, String phoneNumber, String country, String provider) {
        if (tenantId == null) {
            throw new ValidationException("Tenant ID is required");
        }
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new ValidationException("Phone number is required");
        }

        PhoneNumber number = new PhoneNumber();
        number.setTenantId(tenantId);
        number.setPhoneNumber(phoneNumber.trim());
        number.setCountry(country != null && !country.isBlank() ? country.trim() : "US");
        number.setProvider(provider != null && !provider.isBlank() ? provider.trim() : "Twilio");
        number.setStatus("UNASSIGNED");

        return phoneNumberDao.save(number);
    }

    /**
     * Assigns a published IVR flow to a phone number.
     * Validates that the flow's VXML scenario file exists in IVR-engine/scenarios directory.
     * Executes add_extension.sh to provision Asterisk dialplan.
     * Rejects draft/unpublished flows and fails if add_extension.sh fails.
     */
    public PhoneNumber assignIvrFlow(UUID tenantId, UUID phoneId, String flowId, String flowName) {
        if (tenantId == null || phoneId == null) {
            throw new ValidationException("Tenant ID and Phone ID are required");
        }

        PhoneNumber existing = phoneNumberDao.findById(tenantId, phoneId);
        if (existing == null) {
            throw new ValidationException("Phone number not found for this tenant");
        }

        String rawName = (flowName != null && !flowName.isBlank()) ? flowName : flowId;
        String businessName = FlowPublishService.resolveBusinessName(tenantId.toString(), flowId, rawName);

        // 1. Verify flow publication status by checking if VXML scenario file exists in scenarios directory
        String scenariosDirStr = LlmConfig.getScenariosDir();
        Path scenariosDir = Paths.get(scenariosDirStr);
        Path vxmlPath = scenariosDir.resolve(businessName + ".vxml");

        if (!Files.exists(vxmlPath)) {
            logger.warn("[PhoneNumberService] Attempted to assign unpublished flow '{}' (businessName: {}). VXML file not found at {}",
                    rawName, businessName, vxmlPath.toAbsolutePath());
            throw new ValidationException("Cannot assign unpublished/draft flow: '" + rawName + "'. Please publish the flow in IVR Builder first.");
        }

        // 2. Extract digits/extension to register in Asterisk
        String extDigits = existing.getPhoneNumber().replaceAll("[^0-9]", "");
        if (extDigits.isEmpty()) {
            extDigits = "1000";
        }

        // 3. Execute add_extension.sh to register dialplan in Asterisk
        FlowPublishService.ScriptExecutionResult scriptResult = flowPublishService.executeAddExtensionScript(
                tenantId.toString(), flowId, extDigits, rawName, vxmlPath
        );

        if (!scriptResult.isSuccess()) {
            String errorMsg = !scriptResult.getStderr().isBlank() ? scriptResult.getStderr() : scriptResult.getStdout();
            logger.error("[PhoneNumberService] Failed to provision Asterisk dialplan for number {} with flow {}: {}",
                    existing.getPhoneNumber(), businessName, errorMsg);
            throw new ServiceException("Asterisk dialplan provisioning failed (exit code " + scriptResult.getExitCode() + "): " + errorMsg);
        }

        logger.info("[PhoneNumberService] Asterisk dialplan provisioned successfully for number {} -> extension {} -> scenario {}",
                existing.getPhoneNumber(), extDigits, businessName);

        // 4. Update DB state only AFTER successful Asterisk provisioning
        boolean updated = phoneNumberDao.updateAssignment(tenantId, phoneId, flowId, rawName, "ACTIVE");
        if (!updated) {
            throw new ServiceException("Failed to update database record for phone number assignment");
        }

        return phoneNumberDao.findById(tenantId, phoneId);
    }

    /**
     * Lists published flows available in IVR-engine/scenarios directory.
     */
    public List<Map<String, String>> getPublishedFlows(UUID tenantId) {
        List<Map<String, String>> publishedFlows = new ArrayList<>();
        try {
            String scenariosDirStr = LlmConfig.getScenariosDir();
            File dir = new File(scenariosDirStr);
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles((d, name) -> name.endsWith(".vxml"));
                if (files != null) {
                    for (File f : files) {
                        String name = f.getName().replace(".vxml", "");
                        Map<String, String> item = new HashMap<>();
                        item.put("flowId", name);
                        item.put("flowName", name);
                        item.put("businessName", name);
                        publishedFlows.add(item);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("[PhoneNumberService] Error listing published flows: {}", e.getMessage());
        }
        return publishedFlows;
    }
}
