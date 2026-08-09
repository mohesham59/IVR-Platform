package com.nexusivr.payment.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Thread-safe, centralized configuration manager for IVR-payment-service.
 *
 * <p>Loads Paymob credentials and service settings from environment variables or a local {@code .env} file.
 * Implements strict fail-fast validation on startup to ensure no required Paymob environment variables
 * are missing or empty.
 */
public class PaymobConfig {

    private static final Logger logger = LoggerFactory.getLogger(PaymobConfig.class);

    // Required Paymob environment variables
    public static final String KEY_PAYMOB_API_KEY = "PAYMOB_API_KEY";
    public static final String KEY_PAYMOB_SECRET_KEY = "PAYMOB_SECRET_KEY";
    public static final String KEY_PAYMOB_PUBLIC_KEY = "PAYMOB_PUBLIC_KEY";
    public static final String KEY_PAYMOB_HMAC_SECRET = "PAYMOB_HMAC_SECRET";
    public static final String KEY_PAYMOB_INTEGRATION_ID_CARD = "PAYMOB_INTEGRATION_ID_CARD";
    public static final String KEY_PAYMOB_INTEGRATION_ID_WALLET = "PAYMOB_INTEGRATION_ID_WALLET";
    public static final String KEY_PAYMOB_MOTO_INTEGRATION_ID = "PAYMOB_MOTO_INTEGRATION_ID";
    public static final String KEY_PAYMOB_IFRAME_ID = "PAYMOB_IFRAME_ID";

    // Optional service configuration keys
    public static final String KEY_PAYMENT_SERVICE_PORT = "PAYMENT_SERVICE_PORT";

    private static final List<String> REQUIRED_KEYS = List.of(
            KEY_PAYMOB_API_KEY,
            KEY_PAYMOB_SECRET_KEY,
            KEY_PAYMOB_PUBLIC_KEY,
            KEY_PAYMOB_HMAC_SECRET,
            KEY_PAYMOB_INTEGRATION_ID_CARD,
            KEY_PAYMOB_INTEGRATION_ID_WALLET,
            KEY_PAYMOB_MOTO_INTEGRATION_ID,
            KEY_PAYMOB_IFRAME_ID
    );

    private static volatile PaymobConfig instance;

    private final String apiKey;
    private final String secretKey;
    private final String publicKey;
    private final String hmacSecret;
    private final String cardIntegrationId;
    private final String walletIntegrationId;
    private final String motoIntegrationId;
    private final String iframeId;
    private final int servicePort;

    /**
     * Private constructor that resolves configuration from environment / .env and validates presence.
     */
    private PaymobConfig(Map<String, String> customProps, boolean isTesting) {
        Properties envProps = new Properties();
        if (!isTesting) {
            loadDotEnv(envProps);
        }

        List<String> missingKeys = new ArrayList<>();

        this.apiKey = resolveKey(KEY_PAYMOB_API_KEY, customProps, envProps);
        this.secretKey = resolveKey(KEY_PAYMOB_SECRET_KEY, customProps, envProps);
        this.publicKey = resolveKey(KEY_PAYMOB_PUBLIC_KEY, customProps, envProps);
        this.hmacSecret = resolveKey(KEY_PAYMOB_HMAC_SECRET, customProps, envProps);
        this.cardIntegrationId = resolveKey(KEY_PAYMOB_INTEGRATION_ID_CARD, customProps, envProps);
        this.walletIntegrationId = resolveKey(KEY_PAYMOB_INTEGRATION_ID_WALLET, customProps, envProps);
        this.motoIntegrationId = resolveKey(KEY_PAYMOB_MOTO_INTEGRATION_ID, customProps, envProps);
        this.iframeId = resolveKey(KEY_PAYMOB_IFRAME_ID, customProps, envProps);

        // Validate presence of all required keys
        for (String reqKey : REQUIRED_KEYS) {
            String val = resolveKey(reqKey, customProps, envProps);
            if (val == null || val.isBlank() || val.startsWith("your_") || val.startsWith("<MISSING")) {
                missingKeys.add(reqKey);
            }
        }

        if (!missingKeys.isEmpty()) {
            String errMessage = "Paymob configuration initialization failed. Missing or unconfigured environment variables: " 
                    + String.join(", ", missingKeys) + ". Please populate these in your environment or local .env file.";
            logger.error(errMessage);
            throw new PaymobConfigException(errMessage);
        }

        String portStr = resolveKey(KEY_PAYMENT_SERVICE_PORT, customProps, envProps);
        int parsedPort = 8082;
        if (portStr != null && !portStr.isBlank()) {
            try {
                parsedPort = Integer.parseInt(portStr.trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid PAYMENT_SERVICE_PORT '{}', using default 8082", portStr);
            }
        }
        this.servicePort = parsedPort;

        logConfigStatus();
    }

    /**
     * Singleton instance accessor. Initializes and validates configuration on first invocation.
     */
    public static PaymobConfig getInstance() {
        if (instance == null) {
            synchronized (PaymobConfig.class) {
                if (instance == null) {
                    instance = new PaymobConfig(Collections.emptyMap(), false);
                }
            }
        }
        return instance;
    }

    /**
     * Helper for testing or explicit re-initialization with custom properties map.
     */
    public static synchronized PaymobConfig initForTesting(Map<String, String> testProps) {
        instance = new PaymobConfig(testProps, true);
        return instance;
    }

    /**
     * Resets the singleton instance (useful for unit tests).
     */
    public static synchronized void resetInstance() {
        instance = null;
    }

    private String resolveKey(String key, Map<String, String> customProps, Properties envProps) {
        // 1. Custom props map (e.g. passed in unit tests)
        if (customProps != null && customProps.containsKey(key)) {
            String val = customProps.get(key);
            if (val != null && !val.isBlank()) return val.trim();
        }

        // 2. JVM System Property (e.g. -DPAYMOB_API_KEY=xxx)
        String val = System.getProperty(key);
        if (val != null && !val.isBlank()) return val.trim();

        // 3. OS Environment variable (e.g. export PAYMOB_API_KEY=xxx)
        val = System.getenv(key);
        if (val != null && !val.isBlank()) return val.trim();

        // 4. .env file in working directory or parent directory
        val = envProps.getProperty(key);
        if (val != null && !val.isBlank()) return val.trim();

        return null; // Return null if not set — never fallback to fake defaults
    }

    private void loadDotEnv(Properties targetProps) {
        File[] candidates = new File[] {
                new File(".env"),
                new File("../.env"),
                new File("IVR-payment-service/.env")
        };

        for (File f : candidates) {
            if (f.exists() && f.isFile()) {
                parseDotEnvFile(f, targetProps);
                return;
            }
        }
    }

    private void parseDotEnvFile(File file, Properties targetProps) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx <= 0) {
                    continue;
                }
                String key = line.substring(0, eqIdx).trim();
                String value = line.substring(eqIdx + 1).trim();
                if ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                if (!key.isEmpty()) {
                    targetProps.setProperty(key, value);
                }
            }
            logger.info("PaymobConfig: Loaded .env file successfully from {}", file.getAbsolutePath());
        } catch (Exception e) {
            logger.warn("PaymobConfig: Failed to read .env file at {}: {}", file.getAbsolutePath(), e.getMessage());
        }
    }

    /**
     * Logs the configuration status safely.
     * Log ONLY whether a variable is present (boolean), NEVER log secret values.
     */
    private void logConfigStatus() {
        Map<String, String> statusMap = new LinkedHashMap<>();
        for (String reqKey : REQUIRED_KEYS) {
            String val = resolveKey(reqKey, Collections.emptyMap(), new Properties());
            statusMap.put(reqKey, (val != null && !val.isBlank()) ? "PRESENT" : "MISSING");
        }
        logger.info("PaymobConfig: Initialization completed successfully. Configuration Status: {}", statusMap);
    }

    // Getters for Paymob configuration
    public String getApiKey() {
        return apiKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getHmacSecret() {
        return hmacSecret;
    }

    public String getCardIntegrationId() {
        return cardIntegrationId;
    }

    public String getIntegrationIdCard() {
        return cardIntegrationId;
    }

    public String getWalletIntegrationId() {
        return walletIntegrationId;
    }

    public String getMotoIntegrationId() {
        return motoIntegrationId;
    }

    public String getIframeId() {
        return iframeId;
    }

    public int getServicePort() {
        return servicePort;
    }
}
