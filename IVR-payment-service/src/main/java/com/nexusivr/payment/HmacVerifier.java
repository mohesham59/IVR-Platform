package com.nexusivr.payment;

import com.nexusivr.payment.config.PaymobConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Utility for verifying Paymob webhook HMAC-SHA512 signatures.
 */
public class HmacVerifier {

    private static final Logger logger = LoggerFactory.getLogger(HmacVerifier.class);

    /**
     * Canonical 20 field names defined by Paymob for HMAC calculation.
     * Note: 'error_occured' is Paymob's exact canonical spelling (single 'r').
     */
    public static final List<String> HMAC_FIELDS = Arrays.asList(
            "amount_cents",
            "created_at",
            "currency",
            "error_occured",
            "has_parent_transaction",
            "id",
            "integration_id",
            "is_3d_secure",
            "is_auth",
            "is_capture",
            "is_standalone_payment",
            "is_voided",
            "order.id",
            "owner",
            "pending",
            "source_data.pan",
            "source_data.sub_type",
            "source_data.type",
            "success",
            "txn_response_code"
    );

    /**
     * Verifies the HMAC-SHA512 signature for a Paymob transaction payload using the default PAYMOB_HMAC_SECRET.
     */
    public static boolean isValid(Map<String, Object> payload, String receivedHmac) {
        String hmacSecret = PaymobConfig.getInstance().getHmacSecret();
        return isValid(payload, receivedHmac, hmacSecret);
    }

    /**
     * Verifies the HMAC-SHA512 signature for a Paymob transaction payload with a specified secret.
     */
    public static boolean isValid(Map<String, Object> payload, String receivedHmac, String hmacSecret) {
        if (payload == null || receivedHmac == null || receivedHmac.trim().isEmpty()) {
            logger.warn("HMAC verification failed: missing payload or received HMAC.");
            return false;
        }

        if (hmacSecret == null || hmacSecret.trim().isEmpty()) {
            logger.error("HMAC verification failed: PAYMOB_HMAC_SECRET is missing or empty.");
            return false;
        }

        String computedHmac = calculateHmac(payload, hmacSecret);
        if (computedHmac == null) {
            return false;
        }

        byte[] a = computedHmac.toLowerCase().getBytes(StandardCharsets.UTF_8);
        byte[] b = receivedHmac.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);

        boolean matches = MessageDigest.isEqual(a, b);
        if (!matches) {
            logger.warn("HMAC mismatch! Computed: {}, Received: {}", computedHmac, receivedHmac);
        }
        return matches;
    }

    /**
     * Concatenates the values of the 20 lexicographically sorted fields and computes HMAC-SHA512 hex string.
     */
    public static String calculateHmac(Map<String, Object> payload, String hmacSecret) {
        StringBuilder sb = new StringBuilder();

        for (String field : HMAC_FIELDS) {
            Object val = extractFieldValue(payload, field);
            if (val == null) {
                logger.warn("HMAC payload missing required field: '{}'", field);
                return null;
            }
            sb.append(formatValue(val));
        }

        String concatenated = sb.toString();
        return computeHmacSha512Hex(concatenated, hmacSecret);
    }

    private static Object extractFieldValue(Map<String, Object> payload, String field) {
        if (payload.containsKey(field)) {
            return payload.get(field);
        }

        // Support nested keys like order.id or source_data.pan
        if (field.contains(".")) {
            String[] parts = field.split("\\.", 2);
            Object parent = payload.get(parts[0]);
            if (parent instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> parentMap = (Map<String, Object>) parent;
                return parentMap.get(parts[1]);
            }
        }
        return null;
    }

    private static String formatValue(Object val) {
        if (val instanceof Boolean) {
            return ((Boolean) val) ? "true" : "false";
        }
        return String.valueOf(val);
    }

    private static String computeHmacSha512Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            logger.error("Error computing HMAC-SHA512: {}", e.getMessage(), e);
            return null;
        }
    }
}
