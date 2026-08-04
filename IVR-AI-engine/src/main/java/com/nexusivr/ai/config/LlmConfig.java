package com.nexusivr.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;


/**
 * Thread-safe, centralized configuration manager for the NexusIVR AI Backend.
 *
 * <p>Value resolution order (highest priority first):
 * <ol>
 *   <li>JVM System property  (e.g. {@code -Dai.provider=ollama})</li>
 *   <li>OS environment variable (e.g. {@code export AI_PROVIDER=ollama})</li>
 *   <li>.env file in the working directory (e.g. {@code AI_PROVIDER=ollama})</li>
 *   <li>application.properties on the classpath</li>
 *   <li>Built-in sensible default</li>
 * </ol>
 *
 * <p><strong>No API keys or secrets are hardcoded in this class.</strong>
 * Set {@code GEMINI_API_KEY} and/or {@code GROQ_API_KEY} via environment variable or {@code .env} file.
 */
public class LlmConfig {

    private static final Logger logger = LoggerFactory.getLogger(LlmConfig.class);

    /** Properties loaded from classpath {@code application.properties}. */
    private static final Properties appProps = new Properties();

    /** Properties loaded from the {@code .env} file in the working directory. */
    private static final Properties envFileProps = new Properties();

    static {
        // 1. Load application.properties (classpath)
        try (InputStream is = LlmConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (is != null) {
                appProps.load(is);
                logger.info("LlmConfig: Loaded application.properties successfully.");
            } else {
                logger.warn("LlmConfig: application.properties not found on classpath — using defaults.");
            }
        } catch (Exception e) {
            logger.warn("LlmConfig: Could not load application.properties: {}", e.getMessage());
        }

        // 2. Load .env file from the working directory (or classpath as fallback)
        loadDotEnv();
    }

    // ----------------------------------------------------------------
    // .env loading
    // ----------------------------------------------------------------

    private static void loadDotEnv() {
        // Try working directory first
        File envFile = new File(".env");
        if (envFile.exists() && envFile.isFile()) {
            parseDotEnvFile(envFile);
            return;
        }

        // Try parent directory (e.g. when running from target/ subdirectory)
        File parentEnvFile = new File("../.env");
        if (parentEnvFile.exists() && parentEnvFile.isFile()) {
            parseDotEnvFile(parentEnvFile);
            return;
        }

        // Try classpath (useful in tests)
        try (InputStream is = LlmConfig.class.getClassLoader().getResourceAsStream(".env")) {
            if (is != null) {
                parseDotEnvStream(is);
                return;
            }
        } catch (Exception e) {
            // ignore
        }

        logger.info("LlmConfig: No .env file found — relying on environment variables and application.properties.");
    }

    private static void parseDotEnvFile(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            parseLines(reader);
            logger.info("LlmConfig: Loaded .env file from: {}", file.getAbsolutePath());
        } catch (Exception e) {
            logger.warn("LlmConfig: Could not read .env file at {}: {}", file.getAbsolutePath(), e.getMessage());
        }
    }

    private static void parseDotEnvStream(InputStream is) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            parseLines(reader);
            logger.info("LlmConfig: Loaded .env from classpath.");
        } catch (Exception e) {
            logger.warn("LlmConfig: Could not parse classpath .env: {}", e.getMessage());
        }
    }

    private static void parseLines(BufferedReader reader) throws Exception {
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            // Skip blanks and comments
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eqIdx = line.indexOf('=');
            if (eqIdx <= 0) {
                continue;
            }
            String key = line.substring(0, eqIdx).trim();
            String value = line.substring(eqIdx + 1).trim();
            // Strip surrounding quotes
            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            if (!key.isEmpty()) {
                envFileProps.setProperty(key, value);
            }
        }
    }

    // ----------------------------------------------------------------
    // Provider configuration
    // ----------------------------------------------------------------

    /** Returns the active LLM provider name (gemini | groq | ollama | mock).
     *  Default: gemini (Groq is the automatic 429-fallback in UnifiedAiEngine).
     *  Note: openai and claude providers have been removed. */
    public static String getProvider() {
        return resolve("ai.provider", "AI_PROVIDER", "gemini").toLowerCase().trim();
    }

    // ----------------------------------------------------------------
    // Gemini configuration
    // ----------------------------------------------------------------

    /** Gemini API key. MUST be set via GEMINI_API_KEY env var or .env — never hardcoded. */
    public static String getGeminiApiKey() {
        return resolve("gemini.apiKey", "GEMINI_API_KEY", "").trim();
    }

    /** Gemini model identifier. Default: gemini-2.5-flash. */
    public static String getGeminiModel() {
        return resolve("gemini.model", "GEMINI_MODEL", "gemini-2.5-flash").trim();
    }

    /** Gemini model for Stage 1 planning. Default: gemini-2.0-flash. */
    public static String getGeminiPlanningModel() {
        return resolve("gemini.model.planning", "GEMINI_MODEL_PLANNING", getGeminiModel()).trim();
    }

    /** Gemini model for Stage 3 generation. Default: gemini-2.0-flash. */
    public static String getGeminiGenerationModel() {
        return resolve("gemini.model.generation", "GEMINI_MODEL_GENERATION", getGeminiModel()).trim();
    }

    /** Gemini API base URL. Default: https://generativelanguage.googleapis.com/v1beta. */
    public static String getGeminiBaseUrl() {
        return resolve("gemini.baseUrl", "GEMINI_BASE_URL", "https://generativelanguage.googleapis.com/v1beta").trim();
    }

    /** Gemini request timeout in seconds. Default: 30. */
    public static int getGeminiTimeout() {
        return parseIntOrDefault(resolve("gemini.timeout", "GEMINI_TIMEOUT", "30"), 30);
    }

    // ----------------------------------------------------------------
    // Groq configuration
    // ----------------------------------------------------------------

    /** Groq API key. MUST be set via environment variable or .env — never hardcoded. */
    public static String getGroqApiKey() {
        return resolve("groq.apiKey", "GROQ_API_KEY", "").trim();
    }

    /** Groq LLM model identifier.
     *  Bug A fix: changed default from llama-3.1-8b-instant to llama-3.3-70b-versatile.
     *  The 8b-instant model has a much tighter rate-limit quota and was causing consistent
     *  429 errors for generation (Stage 3) requests when the frontend omitted the model param.
     *  70b-versatile is also the first entry in ProviderManager.PROVIDER_MODELS for groq,
     *  keeping the default consistent with ProviderManager's own model-fallback order.
     *  Override via GROQ_MODEL env var or groq.model property for environments where
     *  a lighter model is preferred (e.g. llama-3.1-8b-instant for planning-only calls). */
    public static String getGroqModel() {
        return resolve("groq.model", "GROQ_MODEL", "llama-3.3-70b-versatile").trim();
    }

    /** Groq model for Stage 1 planning. Default: llama-3.1-8b-instant. */
    public static String getGroqPlanningModel() {
        return resolve("groq.model.planning", "GROQ_MODEL_PLANNING", "llama-3.1-8b-instant").trim();
    }

    /** Groq model for Stage 3 generation. Default: llama-3.3-70b-versatile. */
    public static String getGroqGenerationModel() {
        return resolve("groq.model.generation", "GROQ_MODEL_GENERATION", getGroqModel()).trim();
    }

    /** Groq API base URL. Default: https://api.groq.com/openai/v1. */
    public static String getGroqBaseUrl() {
        String url = resolve("groq.baseUrl", "GROQ_BASE_URL", "https://api.groq.com/openai/v1").trim();
        if (url.contains("gsk_") || url.contains("api_key") || url.contains("apikey")) {
            logger.warn("LlmConfig: Groq base URL appears to contain an API key. This is a security risk. Using default base URL instead.");
            return "https://api.groq.com/openai/v1";
        }
        return url;
    }

    /** Groq request timeout in seconds. Default: 30. */
    public static int getGroqTimeout() {
        return parseIntOrDefault(resolve("groq.timeout", "GROQ_TIMEOUT", "30"), 30);
    }

    // ----------------------------------------------------------------
    // Ollama configuration
    // ----------------------------------------------------------------

    /** Ollama server base URL. Default: http://localhost:11434. */
    public static String getOllamaBaseUrl() {
        return resolve("ollama.baseUrl", "OLLAMA_BASE_URL", "http://localhost:11434").trim();
    }

    /** Ollama model name. Default: granite3.2:2b. */
    public static String getOllamaModel() {
        return resolve("ollama.model", "OLLAMA_MODEL", "granite3.2:2b").trim();
    }

    /** Ollama request timeout in seconds. Default: 8. */
    public static int getOllamaTimeout() {
        return parseIntOrDefault(resolve("ollama.timeout", "OLLAMA_TIMEOUT", "8"), 8);
    }

    // ----------------------------------------------------------------
    // OpenRouter configuration
    // ----------------------------------------------------------------

    /** OpenRouter API key. MUST be set via OPENROUTER_API_KEY env var or .env — never hardcoded. */
    public static String getOpenRouterApiKey() {
        return resolve("openrouter.apiKey", "OPENROUTER_API_KEY", "").trim();
    }

    /** OpenRouter API base URL. Default: https://openrouter.ai/api/v1. */
    public static String getOpenRouterBaseUrl() {
        return resolve("openrouter.baseUrl", "OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1").trim();
    }

    /** OpenRouter LLM model identifier. Default: openai/gpt-oss-20b. */
    public static String getOpenRouterModel() {
        return resolve("openrouter.model", "OPENROUTER_MODEL", "openai/gpt-oss-20b").trim();
    }

    /** OpenRouter request timeout in seconds. Default: 35. */
    public static int getOpenRouterTimeout() {
        return parseIntOrDefault(resolve("openrouter.timeout", "OPENROUTER_TIMEOUT", "35"), 35);
    }

    /** Maximum output tokens for LLM generation requests. Default: 8192. */
    public static int getMaxTokens() {
        return parseIntOrDefault(resolve("ai.maxTokens", "AI_MAX_TOKENS", "8192"), 8192);
    }

    // ----------------------------------------------------------------
    // Circuit Breaker configuration
    // ----------------------------------------------------------------


    public static long getCircuitBreakerAuthCooldownMs() {
        return parseLongOrDefault(resolve("circuitbreaker.authCooldownMs", "CIRCUITBREAKER_AUTH_COOLDOWN_MS", "600000"), 600_000L);
    }

    public static long getCircuitBreakerQuotaCooldownMs() {
        return parseLongOrDefault(resolve("circuitbreaker.quotaCooldownMs", "CIRCUITBREAKER_QUOTA_COOLDOWN_MS", "300000"), 300_000L);
    }

    public static long getCircuitBreakerServerErrorCooldownMs() {
        return parseLongOrDefault(resolve("circuitbreaker.serverErrorCooldownMs", "CIRCUITBREAKER_SERVER_ERROR_COOLDOWN_MS", "30000"), 30_000L);
    }

    public static int getCircuitBreakerFailureThreshold() {
        return parseIntOrDefault(resolve("circuitbreaker.failureThreshold", "CIRCUITBREAKER_FAILURE_THRESHOLD", "3"), 3);
    }

    // ----------------------------------------------------------------
    // Database configuration
    // ----------------------------------------------------------------

    /** JDBC URL. Default: jdbc:postgresql://localhost:5432/nexusivr. */
    public static String getDatabaseUrl() {
        return resolve("db.url", "DATABASE_URL", "jdbc:postgresql://localhost:5432/nexusivr").trim();
    }

    /** Database username. Default: nexusivr. */
    public static String getDatabaseUser() {
        return resolve("db.user", "DATABASE_USER", "nexusivr").trim();
    }

    /** Database password. Default: empty string. */
    public static String getDatabasePassword() {
        return resolve("db.password", "DATABASE_PASSWORD", "").trim();
    }

    // ----------------------------------------------------------------
    // Generic property resolution
    // ----------------------------------------------------------------

    /**
     * Resolves a property with the following priority:
     * JVM system property → OS environment variable → .env file → application.properties → default.
     *
     * @param propKey      key in application.properties / JVM -D syntax
     * @param envKey       environment variable name (UPPER_SNAKE_CASE)
     * @param defaultValue fallback when all sources are missing or blank
     * @return resolved value, never null
     */
    public static String resolve(String propKey, String envKey, String defaultValue) {
        // 1. JVM system property (highest priority)
        String v = System.getProperty(propKey);
        if (v != null && !v.isBlank()) return v.trim();

        // 2. OS environment variable
        v = System.getenv(envKey);
        if (v != null && !v.isBlank()) return v.trim();

        // 3. .env file
        v = envFileProps.getProperty(envKey);
        if (v != null && !v.isBlank()) return v.trim();

        // 4. application.properties (classpath)
        v = appProps.getProperty(propKey);
        if (v != null && !v.isBlank()) return v.trim();

        // 5. Default
        return defaultValue;
    }

    /** @deprecated Use {@link #resolve(String, String, String)} */
    @Deprecated
    public static String getProperty(String propKey, String envKey, String defaultValue) {
        return resolve(propKey, envKey, defaultValue);
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private static int parseIntOrDefault(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("LlmConfig: Invalid integer value '{}', using default {}", value, fallback);
            return fallback;
        }
    }

    private static long parseLongOrDefault(String value, long fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("LlmConfig: Invalid long value '{}', using default {}", value, fallback);
            return fallback;
        }
    }

    // ----------------------------------------------------------------
    // Dynamic Project Root & IVR Engine Configuration
    // ----------------------------------------------------------------

    /** Resolves the main repository project root directory dynamically at runtime. */
    public static Path resolveProjectRoot() {
        String customRoot = resolve("ivr.engine.root", "IVR_ENGINE_ROOT", null);
        if (customRoot != null && !customRoot.isBlank()) {
            Path p = Paths.get(customRoot.trim()).toAbsolutePath().normalize();
            if (Files.exists(p)) {
                return p;
            }
        }

        String userDirStr = System.getProperty("user.dir", ".");
        Path userDir = Paths.get(userDirStr).toAbsolutePath().normalize();

        if (Files.exists(userDir.resolve("IVR-engine"))) {
            return userDir;
        }
        if (userDir.getParent() != null && Files.exists(userDir.getParent().resolve("IVR-engine"))) {
            return userDir.getParent();
        }

        try {
            Path codeSourcePath = Paths.get(LlmConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            Path cur = codeSourcePath;
            while (cur != null) {
                if (Files.exists(cur.resolve("IVR-engine"))) {
                    return cur;
                }
                cur = cur.getParent();
            }
        } catch (Exception ignored) {
        }

        return userDir.getParent() != null ? userDir.getParent() : userDir;
    }

    /** Scenarios directory for VXML publication. Dynamic default: <project_root>/IVR-engine/scenarios. */
    public static String getScenariosDir() {
        Path defaultPath = resolveProjectRoot().resolve("IVR-engine").resolve("scenarios");
        String configured = resolve("ivr.engine.scenarios.dir", "IVR_ENGINE_SCENARIOS_DIR", null);
        if (configured == null || configured.isBlank()) {
            return defaultPath.toAbsolutePath().normalize().toString();
        }
        return Paths.get(configured.trim()).toAbsolutePath().normalize().toString();
    }

    /** Drafts directory for VXML draft saving. Dynamic default: <project_root>/IVR-engine/drafts. */
    public static String getDraftsDir() {
        Path defaultPath = resolveProjectRoot().resolve("IVR-engine").resolve("drafts");
        String configured = resolve("ivr.engine.drafts.dir", "IVR_ENGINE_DRAFTS_DIR", null);
        if (configured == null || configured.isBlank()) {
            return defaultPath.toAbsolutePath().normalize().toString();
        }
        return Paths.get(configured.trim()).toAbsolutePath().normalize().toString();
    }

    /** Path to add_extension.sh script for registering Asterisk extensions. Dynamic default: <project_root>/IVR-engine/add_extension.sh. */
    public static String getAddExtensionScriptPath() {
        Path defaultPath = resolveProjectRoot().resolve("IVR-engine").resolve("add_extension.sh");
        String configured = resolve("ivr.engine.add.extension.script", "IVR_ENGINE_ADD_EXTENSION_SCRIPT", null);
        if (configured == null || configured.isBlank()) {
            return defaultPath.toAbsolutePath().normalize().toString();
        }
        return Paths.get(configured.trim()).toAbsolutePath().normalize().toString();
    }
}

