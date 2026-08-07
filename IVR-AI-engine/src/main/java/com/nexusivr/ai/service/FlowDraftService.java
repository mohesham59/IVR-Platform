package com.nexusivr.ai.service;

import com.google.gson.Gson;
import com.nexusivr.ai.config.LlmConfig;
import com.nexusivr.ai.exception.ServiceException;
import com.nexusivr.ai.exception.ValidationException;
import com.nexusivr.ai.model.flow.FlowModel;
import com.nexusivr.ai.model.flow.FlowNodeType;
import com.nexusivr.ai.model.flow.FlowNodeTypeAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service managing IVR flow draft persistence.
 * Drafts are stored in database and separate draft storage directory (IVR_ENGINE_DRAFTS_DIR),
 * completely isolated from the live Asterisk/engine scenario directory (IVR_ENGINE_SCENARIOS_DIR).
 */
public class FlowDraftService {

    private static final Logger logger = LoggerFactory.getLogger(FlowDraftService.class);

    private final ModelToVxmlExporter exporter;
    private final String configuredDraftsDir;

    public FlowDraftService() {
        this(new ModelToVxmlExporter(), null);
    }

    public FlowDraftService(String configuredDraftsDir) {
        this(new ModelToVxmlExporter(), configuredDraftsDir);
    }

    public FlowDraftService(ModelToVxmlExporter exporter, String configuredDraftsDir) {
        this.exporter = exporter != null ? exporter : new ModelToVxmlExporter();
        this.configuredDraftsDir = configuredDraftsDir;
    }

    public String resolveDraftsDir() {
        if (configuredDraftsDir != null && !configuredDraftsDir.isBlank()) {
            return configuredDraftsDir.trim();
        }
        return LlmConfig.getDraftsDir();
    }

    public String saveDraft(String tenantId, String flowId, String flowName, String flowJson) throws IOException {
        return saveDraft(tenantId, flowId, flowName, flowJson, null);
    }

    public String saveDraft(String tenantId, String flowId, String flowName, String flowJson, Integer version) throws IOException {
        if (flowJson == null || flowJson.isBlank()) {
            throw new ValidationException("Cannot save an empty draft");
        }

        String dirPathStr = resolveDraftsDir();
        Path dirPath = Paths.get(dirPathStr);
        Path tenantScopedDir = dirPath;
        if (tenantId != null && !tenantId.isBlank()) {
            tenantScopedDir = dirPath.resolve(tenantId.trim());
        }

        try {
            if (!Files.exists(tenantScopedDir)) {
                Files.createDirectories(tenantScopedDir);
            }
        } catch (Exception e) {
            logger.error("[FlowDraftService] Failed to create draft directory at {}: {}", tenantScopedDir.toAbsolutePath(), e.getMessage());
            throw new ServiceException("Failed to save draft: directory is not writable (" + tenantScopedDir.toAbsolutePath() + ")", e);
        }

        String jsonContent;
        String trimmed = flowJson.trim();
        FlowModel model = null;

        if (trimmed.startsWith("{") && trimmed.contains("\"nodes\"")) {
            model = FlowContextService.convertJsonToModel(trimmed);
        }

        if (model == null && (trimmed.startsWith("{") || trimmed.startsWith("["))) {
            try {
                model = new com.google.gson.GsonBuilder()
                        .registerTypeAdapter(FlowNodeType.class, new com.nexusivr.ai.model.flow.FlowNodeTypeAdapter())
                        .create()
                        .fromJson(trimmed, FlowModel.class);
            } catch (Exception ignored) {}
        }

        if (model == null && (trimmed.startsWith("<") || trimmed.contains("<vxml"))) {
            try {
                model = FlowContextService.convertVxmlToModel(trimmed);
            } catch (Exception ignored) {}
        }

        if (model == null) {
            model = FlowContextService.convertJsonToModel(trimmed);
        }

        if (model != null && model.getNodes() != null && !model.getNodes().isEmpty()) {
            if (flowName != null && !flowName.isBlank()) {
                model.setName(flowName.trim());
            } else if (model.getName() != null && !model.getName().isBlank()) {
                flowName = model.getName().trim();
            }
            jsonContent = new com.google.gson.GsonBuilder()
                    .registerTypeAdapter(FlowNodeType.class, new com.nexusivr.ai.model.flow.FlowNodeTypeAdapter())
                    .setPrettyPrinting()
                    .create()
                    .toJson(model);
        } else {
            logger.error("[FlowDraftService] Cannot save draft: flow data could not be parsed into a valid FlowModel");
            throw new ValidationException("Could not save — flow contains invalid node data or no nodes");
        }


        if (jsonContent == null || jsonContent.isBlank() || !jsonContent.contains("nodes")) {
            logger.error("[FlowDraftService] Exported JSON content is empty or invalid");
            throw new ValidationException("Could not save — exported JSON content is empty or invalid");
        }

        int targetVersion;
        if (version != null && version > 0) {
            targetVersion = version;
        } else {
            targetVersion = getNextDraftVersion(tenantScopedDir, tenantId, flowId, flowName);
        }

        String filename = buildDraftFilename(tenantId, flowId, flowName, targetVersion);
        Path targetPath = tenantScopedDir.resolve(filename);

        try {
            byte[] bytes = jsonContent.getBytes(StandardCharsets.UTF_8);
            try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(
                    targetPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.SYNC)) {
                channel.write(java.nio.ByteBuffer.wrap(bytes));
                channel.force(true); // Force OS write to disk (fsync)
            }

            // Verify existence and non-zero length on disk immediately
            if (!Files.exists(targetPath) || Files.size(targetPath) == 0) {
                logger.error("[FlowDraftService] Verification failed: File on disk at {} is missing or 0 bytes after write", targetPath.toAbsolutePath());
                throw new ServiceException("Failed to save draft: write verification failed for " + targetPath.toAbsolutePath(), null);
            }

            logger.info("[FlowDraftService] Saved and verified draft JSON successfully to {} (version v{}, size={} bytes)",
                    targetPath.toAbsolutePath(), targetVersion, Files.size(targetPath));
        } catch (IOException e) {
            logger.error("[FlowDraftService] Failed to write draft file to {}: {}", targetPath.toAbsolutePath(), e.getMessage());
            throw new ServiceException("Failed to save draft file to " + targetPath.toAbsolutePath() + ": " + e.getMessage(), e);
        }

        return targetPath.toAbsolutePath().toString();
    }

    public static int getNextDraftVersion(Path dirPath, String tenantId, String flowId, String flowName) {
        String baseName = getDraftBaseName(tenantId, flowId, flowName);
        Pattern versionPattern = Pattern.compile(Pattern.quote(baseName) + "_draft_v(\\d+)\\.json");
        Pattern legacyPattern = Pattern.compile(Pattern.quote(baseName) + "_draft\\.json");
        int maxVersion = 0;
        try (var stream = Files.list(dirPath)) {
            for (Path path : stream.toList()) {
                String fname = path.getFileName().toString();
                Matcher matcher = versionPattern.matcher(fname);
                if (matcher.matches()) {
                    try {
                        int v = Integer.parseInt(matcher.group(1));
                        if (v > maxVersion) {
                            maxVersion = v;
                        }
                    } catch (NumberFormatException ignored) {}
                } else if (legacyPattern.matcher(fname).matches()) {
                    if (maxVersion < 1) {
                        maxVersion = 1;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("[FlowDraftService] Error scanning directory for draft versions at {}: {}", dirPath, e.getMessage());
        }
        return maxVersion + 1;
    }

    public static String buildDraftFilename(String tenantId, String flowId, String flowName, Integer version) {
        String baseName = getDraftBaseName(tenantId, flowId, flowName);
        String versionSuffix = (version != null && version > 0) ? "_draft_v" + version : "_draft";
        return baseName + versionSuffix + ".json";
    }

    public static boolean isSpokenGreeting(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.trim().toLowerCase();
        return lower.startsWith("welcome") ||
               lower.startsWith("thank you") ||
               lower.startsWith("thanks for") ||
               lower.startsWith("hello") ||
               lower.startsWith("please") ||
               lower.contains("press 1") ||
               lower.contains("press 2") ||
               lower.contains("listen carefully") ||
               lower.contains("calls may be recorded") ||
               lower.length() > 60;
    }

    public static String getBaseName(String tenantId, String flowId, String flowName) {
        String baseName = null;
        String cleanFlowName = flowName;
        if (cleanFlowName != null) {
            cleanFlowName = cleanFlowName.replaceAll("^[\\s\\\\n\\\\r]+", "").replaceAll("[\\s\\\\n\\\\r]+$", "").trim();
        }

        if (cleanFlowName != null && !cleanFlowName.isBlank() && !isSpokenGreeting(cleanFlowName)) {
            baseName = cleanFlowName.toLowerCase().trim()
                    .replaceAll("[^a-z0-9]+", "_")
                    .replaceAll("^_+|_+$", "");
        }
        if (baseName == null || baseName.isBlank()) {
            baseName = "ivr_flow";
        }

        if (baseName.length() > 60) {
            String truncated = baseName.substring(0, 60);
            int lastUnderscore = truncated.lastIndexOf('_');
            if (lastUnderscore > 0) {
                baseName = truncated.substring(0, lastUnderscore);
            } else {
                baseName = truncated;
            }
            baseName = baseName.replaceAll("_+$", "");
        }
        
        return baseName;
    }

    public static String getDraftBaseName(String tenantId, String flowId, String flowName) {
        String baseName = getBaseName(tenantId, flowId, flowName);
        int maxLen = 60;
        if (baseName.length() > maxLen) {
            String truncated = baseName.substring(0, maxLen);
            int lastUnderscore = truncated.lastIndexOf('_');
            if (lastUnderscore > 0) {
                baseName = truncated.substring(0, lastUnderscore);
            } else {
                baseName = truncated;
            }
            baseName = baseName.replaceAll("_+$", "");
        }
        return baseName;
    }
}

