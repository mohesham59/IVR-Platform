package com.nexusivr.ai.service;

import com.nexusivr.ai.config.LlmConfig;
import com.nexusivr.ai.dto.response.FlowValidationResponse;
import com.nexusivr.ai.exception.ServiceException;
import com.nexusivr.ai.exception.ValidationException;
import com.nexusivr.ai.model.flow.FlowModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Service managing VXML generation and publication to the IVR engine scenarios directory,
 * as well as Asterisk extension registration via add_extension.sh.
 */
public class FlowPublishService {

    private static final Logger logger = LoggerFactory.getLogger(FlowPublishService.class);

    private final ModelToVxmlExporter vxmlExporter;
    private final FlowModelValidator validator;
    private final String configuredScenariosDir;

    public FlowPublishService() {
        this(new ModelToVxmlExporter(), new FlowModelValidator(), null);
    }

    public FlowPublishService(ModelToVxmlExporter vxmlExporter, FlowModelValidator validator, String configuredScenariosDir) {
        this.vxmlExporter = vxmlExporter;
        this.validator = validator;
        this.configuredScenariosDir = configuredScenariosDir;
    }

    public String resolveScenariosDir() {
        if (configuredScenariosDir != null && !configuredScenariosDir.isBlank()) {
            return configuredScenariosDir.trim();
        }
        return LlmConfig.getScenariosDir();
    }

    /**
     * Converts a human-readable flow/business name into a safe filesystem and Asterisk-compatible slug.
     * Rules: lowercase, spaces→underscores, strip non-alphanumeric-underscore, collapse repeated underscores,
     * strip leading/trailing underscores, truncate to 64 chars, default to "published_flow" when blank.
     */
    public static String sanitizeBusinessName(String name) {
        if (name == null || name.isBlank()) {
            return "published_flow";
        }
        String slug = name.trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")  // replace any run of non-alnum chars with a single underscore
                .replaceAll("^_+|_+$", "");       // strip leading/trailing underscores
        if (slug.isEmpty()) {
            return "published_flow";
        }
        return slug.length() > 64 ? slug.substring(0, 64) : slug;
    }

    /**
     * Resolves the single canonical business name slug for a flow publication.
     * Used identically for:
     * 1. The VXML scenario filename on disk (e.g. {businessName}.vxml)
     * 2. The JSON scenario filename on disk (e.g. {businessName}.json)
     * 3. The business_name argument passed to add_extension.sh (and Asterisk AGI dialplan)
     * 4. The base name for draft versioning ({businessName}_draft_vN.vxml)
     */
    public static String resolveBusinessName(String tenantId, String flowId, String flowName) {
        return FlowDraftService.getBaseName(null, flowId, flowName);
    }

    /**
     * Builds the VXML output filename derived from the resolved business name slug.
     */
    public static String buildFilename(String tenantId, String flowId, String extension, String flowName) {
        String businessName = resolveBusinessName(tenantId, flowId, flowName);
        return businessName + ".vxml";
    }

    public FlowPublishResult publishFlow(String tenantId, String flowId, String extension, String flowName, String flowJson) throws IOException {
        if (flowJson == null || flowJson.isBlank()) {
            throw new ValidationException("Cannot publish an empty or null flow");
        }

        String trimmed = flowJson.trim();
        FlowModel model = null;
        if (trimmed.startsWith("<")) {
            model = FlowContextService.convertVxmlToModel(trimmed);
        } else {
            model = FlowContextService.convertJsonToModel(trimmed);
            if (model == null) {
                model = FlowContextService.convertVxmlToModel(trimmed);
            }
        }

        if (model == null || model.getNodes() == null || model.getNodes().isEmpty()) {
            throw new ValidationException("Flow contains no nodes to publish");
        }

        FlowValidationResponse validation = validator.validate(model);
        if (!validation.isValid()) {
            String firstError = validation.getIssues().stream()
                    .filter(i -> "error".equalsIgnoreCase(i.getSeverity().name()))
                    .map(i -> i.getMessage())
                    .findFirst()
                    .orElse("Flow validation failed");
            throw new ValidationException("Flow validation error: " + firstError);
        }

        String vxml = vxmlExporter.export(model);
        String dirPathStr = resolveScenariosDir();
        Path dirPath = Paths.get(dirPathStr);

        // Publish flat into the scenarios root. The IVR engine's VxmlLoader only
        // resolves files from scenarios/<name>.vxml and the AGI handler sanitizes
        // scenario names to [A-Za-z0-9_-] (no '/'), so tenant-scoped subdirectories
        // are not loadable. Business names are slugified from flowId+flowName, which
        // keeps collisions unlikely even without tenant scoping.
        Path tenantScopedDir = dirPath;

        try {
            if (!Files.exists(tenantScopedDir)) {
                Files.createDirectories(tenantScopedDir);
            }
        } catch (Exception e) {
            logger.error("[FlowPublishService] Directory creation failed at {}: {}", tenantScopedDir.toAbsolutePath(), e.getMessage());
            throw new ServiceException("Failed to publish VXML scenario: directory is not writable (" + tenantScopedDir.toAbsolutePath() + ")", e);
        }

        String businessName = resolveBusinessName(tenantId, flowId, flowName);
        String filename = businessName + ".vxml";
        Path targetPath = tenantScopedDir.resolve(filename);

        try {
            Files.writeString(targetPath, vxml, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            logger.info("[FlowPublishService] Published VXML scenario successfully to {}", targetPath.toAbsolutePath());
        } catch (IOException e) {
            logger.error("[FlowPublishService] Failed to write VXML file to {}: {}", targetPath.toAbsolutePath(), e.getMessage());
            throw new ServiceException("Failed to write VXML scenario file to " + targetPath.toAbsolutePath() + ": " + e.getMessage(), e);
        }

        // Also write scenario JSON file with identical base name for IVR engine ScenarioLoader
        Path targetJsonPath = tenantScopedDir.resolve(businessName + ".json");
        try {
            Files.writeString(targetJsonPath, flowJson, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            logger.warn("[FlowPublishService] Could not write optional scenario JSON file to {}: {}", targetJsonPath.toAbsolutePath(), e.getMessage());
        }

        ScriptExecutionResult scriptResult = executeAddExtensionScript(tenantId, flowId, extension, flowName, targetPath);
        boolean extensionRegistered = scriptResult.isSuccess();
        String extensionMessage;
        if (extensionRegistered) {
            extensionMessage = !scriptResult.getStdout().isBlank()
                    ? scriptResult.getStdout()
                    : "Extension registered successfully.";
        } else {
            String details = !scriptResult.getStdout().isBlank()
                    ? scriptResult.getStdout()
                    : (!scriptResult.getStderr().isBlank() ? scriptResult.getStderr() : "Script execution failed");
            extensionMessage = "Flow published, but phone extension registration failed (exit code "
                    + scriptResult.getExitCode() + "): " + details;
        }

        return new FlowPublishResult(
                true,
                targetPath.toAbsolutePath().toString(),
                filename,
                vxml,
                validation.getScore(),
                extensionRegistered,
                extensionMessage,
                extensionRegistered ? null : extensionMessage
        );
    }

    /**
     * Resolves an explicit extension or automatically allocates the next available extension
     * by inspecting /etc/asterisk/extensions.conf.
     */
    public static String resolveOrAllocateExtension(String requestedExtension, String businessName) {
        return resolveOrAllocateExtension(requestedExtension, businessName, Paths.get("/etc/asterisk/extensions.conf"));
    }

    public static String resolveOrAllocateExtension(String requestedExtension, String businessName, Path confPath) {
        if (requestedExtension != null && !requestedExtension.isBlank()) {
            return requestedExtension.trim();
        }

        String safeBusiness = sanitizeBusinessName(businessName);
        if (confPath != null && Files.exists(confPath)) {
            try {
                List<String> lines = Files.readAllLines(confPath, StandardCharsets.UTF_8);

                // 1. Check if businessName is already registered -> reuse its extension
                java.util.regex.Pattern bizPattern = java.util.regex.Pattern.compile(
                        "^exten\\s*=>\\s*(\\d+),\\s*1,\\s*NoOp\\(.*" + java.util.regex.Pattern.quote(safeBusiness) + ".*\\)",
                        java.util.regex.Pattern.CASE_INSENSITIVE);
                for (String line : lines) {
                    java.util.regex.Matcher m = bizPattern.matcher(line.trim());
                    if (m.find()) {
                        return m.group(1);
                    }
                }

                // 2. Find highest numeric extension registered
                java.util.regex.Pattern extPattern = java.util.regex.Pattern.compile("^exten\\s*=>\\s*(\\d+),");
                int maxExt = 999;
                for (String line : lines) {
                    java.util.regex.Matcher m = extPattern.matcher(line.trim());
                    if (m.find()) {
                        try {
                            int val = Integer.parseInt(m.group(1));
                            if (val > maxExt) {
                                maxExt = val;
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }

                if (maxExt >= 1000) {
                    return String.valueOf(maxExt + 1);
                }
            } catch (Exception e) {
                logger.warn("[FlowPublishService] Error reading extensions.conf for allocation: {}", e.getMessage());
            }
        }

        return "1000";
    }

    public ScriptExecutionResult executeAddExtensionScript(String extension, String flowName, Path targetPath) {
        return executeAddExtensionScript(null, null, extension, flowName, targetPath);
    }

    public ScriptExecutionResult executeAddExtensionScript(String tenantId, String flowId, String extension, String flowName, Path targetPath) {
        // Use the exact same resolved business name as the VXML & JSON scenario filenames on disk
        String businessName = resolveBusinessName(tenantId, flowId, flowName);
        String extToRegister = resolveOrAllocateExtension(extension, businessName);
        String configuredScript = LlmConfig.getAddExtensionScriptPath();

        Path scriptPath = null;

        // 1. Check relative to targetPath (allows custom/test directories to override)
        if (targetPath != null && targetPath.getParent() != null) {
            Path candidate1 = targetPath.getParent().resolve("add_extension.sh");
            if (Files.exists(candidate1)) {
                scriptPath = candidate1;
            } else if (targetPath.getParent().getParent() != null) {
                Path candidate2 = targetPath.getParent().getParent().resolve("add_extension.sh");
                if (Files.exists(candidate2)) {
                    scriptPath = candidate2;
                } else if (targetPath.getParent().getParent().getParent() != null) {
                    Path candidate3 = targetPath.getParent().getParent().getParent().resolve("add_extension.sh");
                    if (Files.exists(candidate3)) {
                        scriptPath = candidate3;
                    }
                }
            }
        }

        // 2. Fall back to configured path from LlmConfig
        if (scriptPath == null) {
            if (configuredScript != null && !configuredScript.isBlank()) {
                Path configuredPath = Paths.get(configuredScript);
                if (Files.exists(configuredPath)) {
                    scriptPath = configuredPath;
                }
            }
        }

        if (scriptPath == null || !Files.exists(scriptPath)) {
            logger.warn("[FlowPublishService] add_extension.sh script not found at configured location: {}", configuredScript);
            return new ScriptExecutionResult(false, 127, "add_extension.sh script not found", "Script path does not exist");
        }

        boolean runWithSudo = false;
        boolean runningAsRoot = "root".equalsIgnoreCase(System.getProperty("user.name"));
        boolean sudoAvailable = Files.isExecutable(Paths.get("/usr/bin/sudo"));
        if (!runningAsRoot && sudoAvailable) {
            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                if (element.getClassName().startsWith("org.junit.") || element.getClassName().startsWith("org.apache.maven.surefire.")) {
                    runWithSudo = false;
                    break;
                }
                runWithSudo = true;
            }
        }

        List<String> command = new ArrayList<>();
        if (runWithSudo) {
            command.add("/usr/bin/sudo");
        }
        command.add("/bin/bash");
        command.add(scriptPath.toAbsolutePath().toString());
        command.add(extToRegister);
        command.add(businessName);

        String vxmlFilePath = businessName;
        command.add(vxmlFilePath);
        
        // Add tenantId as 4th arg
        if (tenantId != null && !tenantId.isBlank()) {
            command.add(tenantId);
        }

        logger.info("[FlowPublishService] Executing add_extension.sh: {} with ext='{}', business='{}', vxml_path='{}', tenant_id='{}'",
                scriptPath.toAbsolutePath(), extToRegister, businessName, vxmlFilePath, tenantId);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            if (scriptPath.getParent() != null) {
                pb.directory(scriptPath.getParent().toFile());
            }
            Process process = pb.start();

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                logger.info("[FlowPublishService] add_extension.sh executed successfully (exit 0). Output: {}", stdout.trim());
                return new ScriptExecutionResult(true, 0, stdout.trim(), stderr.trim());
            } else {
                logger.warn("[FlowPublishService] add_extension.sh failed with exit {}. Stderr: {}, Stdout: {}",
                        exitCode, stderr.trim(), stdout.trim());
                return new ScriptExecutionResult(false, exitCode, stdout.trim(), stderr.trim());
            }
        } catch (Exception e) {
            logger.error("[FlowPublishService] Error executing add_extension.sh: {}", e.getMessage(), e);
            return new ScriptExecutionResult(false, -1, "", e.getMessage());
        }
    }

    public static class ScriptExecutionResult {
        private final boolean success;
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        public ScriptExecutionResult(boolean success, int exitCode, String stdout, String stderr) {
            this.success = success;
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        public boolean isSuccess() { return success; }
        public int getExitCode() { return exitCode; }
        public String getStdout() { return stdout; }
        public String getStderr() { return stderr; }
    }

    public static class FlowPublishResult {
        private final boolean success;
        private final String filePath;
        private final String filename;
        private final String vxmlContent;
        private final int validationScore;
        private final boolean extensionRegistered;
        private final String extensionMessage;
        private final String warning;

        public FlowPublishResult(boolean success, String filePath, String filename, String vxmlContent, int validationScore) {
            this(success, filePath, filename, vxmlContent, validationScore, true, "Extension registered", null);
        }

        public FlowPublishResult(boolean success, String filePath, String filename, String vxmlContent, int validationScore,
                                 boolean extensionRegistered, String extensionMessage, String warning) {
            this.success = success;
            this.filePath = filePath;
            this.filename = filename;
            this.vxmlContent = vxmlContent;
            this.validationScore = validationScore;
            this.extensionRegistered = extensionRegistered;
            this.extensionMessage = extensionMessage;
            this.warning = warning;
        }

        public boolean isSuccess() { return success; }
        public String getFilePath() { return filePath; }
        public String getFilename() { return filename; }
        public String getVxmlContent() { return vxmlContent; }
        public int getValidationScore() { return validationScore; }
        public boolean isExtensionRegistered() { return extensionRegistered; }
        public String getExtensionMessage() { return extensionMessage; }
        public String getWarning() { return warning; }
    }
}
