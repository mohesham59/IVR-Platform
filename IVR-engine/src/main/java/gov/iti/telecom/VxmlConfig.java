package gov.iti.telecom;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * VxmlConfig — centralized configuration for the IVR platform.
 *
 * <h2>HOW IT WORKS</h2>
 * <ol>
 *   <li>Loads configuration from vxml-config.properties (classpath)</li>
 *   <li>Supports environment variable overrides (e.g., VXML_TIMEOUT)</li>
 *   <li>Provides typed getter methods for easy access</li>
 *   <li>Validates configuration on load</li>
 *   <li>Singleton pattern for application-wide access</li>
 * </ol>
 *
 * <h2>USAGE EXAMPLE</h2>
 * <pre>{@code
 * // Load default configuration
 * VxmlConfig config = VxmlConfig.loadFromClasspath();
 *
 * // Access configuration values
 * String resourcePath = config.getVxmlResourcePath();
 * int timeout = config.getSessionTimeoutSeconds();
 * boolean validateOnLoad = config.isValidationEnabled();
 *
 * // Override via environment variable
 * // Set: export VXML_TIMEOUT=300
 * // The getter will return 300 instead of the property value
 * }</pre>
 *
 * <h2>CONFIGURATION FILE</h2>
 * <p>Location: src/main/resources/vxml-config.properties</p>
 * <pre>{@code
 * # Resource Paths
 * vxml.resource.path=scenarios/
 * vxml.resource.classpath.enabled=true
 * vxml.cache.enabled=true
 *
 * # Execution
 * vxml.session.timeout.seconds=600
 * vxml.session.max.active=1000
 *
 * # Validation
 * vxml.validate.on.load=true
 * }</pre>
 *
 * <h2>ENVIRONMENT VARIABLE OVERRIDES</h2>
 * <p>Any property can be overridden via environment variable:</p>
 * <ul>
 *   <li>vxml.resource.path → VXML_RESOURCE_PATH</li>
 *   <li>vxml.session.timeout.seconds → VXML_SESSION_TIMEOUT_SECONDS</li>
 *   <li>vxml.validate.on.load → VXML_VALIDATE_ON_LOAD</li>
 * </ul>
 *
 * @author IVR Platform Team
 * @version 1.0
 */
public class VxmlConfig {

    private static VxmlConfig instance;
    private Properties properties;

    // Default values (hardcoded as fallback)
    private static final String DEFAULT_RESOURCE_PATH = "scenarios/";
    private static final int DEFAULT_SESSION_TIMEOUT = 600;
    private static final int DEFAULT_MAX_ACTIVE_SESSIONS = 1000;
    private static final boolean DEFAULT_CACHING_ENABLED = true;
    private static final boolean DEFAULT_VALIDATION_ENABLED = true;
    private static final String DEFAULT_CONFIG_FILE = "vxml-config.properties";

    /**
     * Private constructor to enforce singleton pattern.
     */
    private VxmlConfig(Properties props) {
        this.properties = props;
    }

    /**
     * Loads configuration from classpath (vxml-config.properties).
     *
     * <p>If the property file is not found, uses default values.</p>
     *
     * @return singleton VxmlConfig instance
     */
    public static synchronized VxmlConfig loadFromClasspath() {
        if (instance == null) {
            Properties props = new Properties();

            // Try to load from classpath
            try (InputStream is = VxmlConfig.class.getClassLoader()
                    .getResourceAsStream(DEFAULT_CONFIG_FILE)) {
                if (is != null) {
                    props.load(is);
                    System.out.println("[VxmlConfig] Loaded configuration from: " + DEFAULT_CONFIG_FILE);
                } else {
                    System.out.println("[VxmlConfig] Config file not found, using defaults: " +
                            DEFAULT_CONFIG_FILE);
                }
            } catch (IOException e) {
                System.err.println("[VxmlConfig] Error loading config file: " + e.getMessage());
            }

            instance = new VxmlConfig(props);
        }
        return instance;
    }

    /**
     * Gets the VXML resource directory path.
     *
     * <p>Default: "scenarios/"<br>
     * Override: VXML_RESOURCE_PATH environment variable</p>
     *
     * @return the resource path (e.g., "scenarios/")
     */
    public String getVxmlResourcePath() {
        return getProperty("vxml.resource.path", DEFAULT_RESOURCE_PATH);
    }

    /**
     * Gets the session execution timeout in seconds.
     *
     * <p>Default: 600 seconds (10 minutes)<br>
     * Override: VXML_SESSION_TIMEOUT_SECONDS environment variable</p>
     *
     * @return timeout in seconds
     */
    public int getSessionTimeoutSeconds() {
        return getIntProperty("vxml.session.timeout.seconds", DEFAULT_SESSION_TIMEOUT);
    }

    /**
     * Gets the maximum number of concurrent active sessions.
     *
     * <p>Default: 1000<br>
     * Override: VXML_SESSION_MAX_ACTIVE environment variable</p>
     *
     * @return maximum active sessions
     */
    public int getMaxActiveSessions() {
        return getIntProperty("vxml.session.max.active", DEFAULT_MAX_ACTIVE_SESSIONS);
    }

    /**
     * Checks if document caching is enabled.
     *
     * <p>Default: true<br>
     * Override: VXML_CACHE_ENABLED environment variable</p>
     *
     * @return true if caching is enabled
     */
    public boolean isCachingEnabled() {
        return getBooleanProperty("vxml.cache.enabled", DEFAULT_CACHING_ENABLED);
    }

    /**
     * Checks if VXML validation should be performed on load.
     *
     * <p>Default: true<br>
     * Override: VXML_VALIDATE_ON_LOAD environment variable</p>
     *
     * @return true if validation is enabled
     */
    public boolean isValidationEnabled() {
        return getBooleanProperty("vxml.validate.on.load", DEFAULT_VALIDATION_ENABLED);
    }

    /**
     * Gets the TTS (Text-to-Speech) provider.
     *
     * <p>Default: "google"<br>
     * Alternatives: "asterisk", "none"</p>
     *
     * @return TTS provider name
     */
    public String getTtsProvider() {
        return getProperty("tts.provider", "google");
    }

    /**
     * Gets the TTS language code.
     *
     * <p>Default: "en-US"</p>
     *
     * @return language code (e.g., "en-US", "fr-FR")
     */
    public String getTtsLanguage() {
        return getProperty("tts.language", "en-US");
    }

    /**
     * Helper: get string property with environment variable override.
     *
     * @param key property key
     * @param defaultValue fallback value
     * @return property value
     */
    private String getProperty(String key, String defaultValue) {
        // Check environment variable first (uppercase with underscores)
        String envKey = key.toUpperCase().replace(".", "_");
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            System.out.println("[VxmlConfig] Using env override: " + envKey + "=" + envValue);
            return envValue;
        }

        // Fall back to properties file
        String propValue = properties.getProperty(key);
        if (propValue != null && !propValue.isEmpty()) {
            return propValue;
        }

        // Fall back to default
        return defaultValue;
    }

    /**
     * Helper: get integer property with environment variable override.
     *
     * @param key property key
     * @param defaultValue fallback value
     * @return property value as integer
     */
    private int getIntProperty(String key, int defaultValue) {
        String value = getProperty(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.err.println("[VxmlConfig] Invalid integer for " + key + ": " + value);
            return defaultValue;
        }
    }

    /**
     * Helper: get boolean property with environment variable override.
     *
     * @param key property key
     * @param defaultValue fallback value
     * @return property value as boolean
     */
    private boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = getProperty(key, String.valueOf(defaultValue));
        return "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) ||
                "1".equals(value);
    }

    /**
     * Resets the singleton instance (useful for testing).
     */
    public static synchronized void reset() {
        instance = null;
    }

    @Override
    public String toString() {
        return "VxmlConfig{" +
                "resourcePath='" + getVxmlResourcePath() + '\'' +
                ", timeoutSeconds=" + getSessionTimeoutSeconds() +
                ", maxActiveSessions=" + getMaxActiveSessions() +
                ", cachingEnabled=" + isCachingEnabled() +
                ", validationEnabled=" + isValidationEnabled() +
                '}';
    }
}
