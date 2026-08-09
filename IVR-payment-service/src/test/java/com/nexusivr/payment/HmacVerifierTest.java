package com.nexusivr.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for Paymob HMAC-SHA512 webhook signature verification.
 */
public class HmacVerifierTest {

    private static final String TEST_HMAC_SECRET = "test_hmac_secret_key_999";
    private Map<String, Object> validPayload;

    @BeforeEach
    public void setUp() {
        validPayload = new HashMap<>();
        validPayload.put("amount_cents", 50000);
        validPayload.put("created_at", "2026-08-08T14:00:00");
        validPayload.put("currency", "EGP");
        validPayload.put("error_occured", false); // Paymob canonical spelling with single 'r'
        validPayload.put("has_parent_transaction", false);
        validPayload.put("id", 99887766);
        validPayload.put("integration_id", 5834828);
        validPayload.put("is_3d_secure", true);
        validPayload.put("is_auth", false);
        validPayload.put("is_capture", false);
        validPayload.put("is_standalone_payment", true);
        validPayload.put("is_voided", false);
        validPayload.put("order.id", 11223344);
        validPayload.put("owner", 554433);
        validPayload.put("pending", false);
        validPayload.put("source_data.pan", "4111");
        validPayload.put("source_data.sub_type", "Visa");
        validPayload.put("source_data.type", "Card");
        validPayload.put("success", true);
        validPayload.put("txn_response_code", "00");
    }

    @Test
    @DisplayName("Test 1: Known payload with correctly derived HMAC passes validation")
    public void testValidHmacVerification() {
        /*
         * Concatenated payload in lexicographical order:
         * 1. amount_cents          -> "50000"
         * 2. created_at            -> "2026-08-08T14:00:00"
         * 3. currency              -> "EGP"
         * 4. error_occured         -> "false"
         * 5. has_parent_transaction -> "false"
         * 6. id                    -> "99887766"
         * 7. integration_id        -> "5834828"
         * 8. is_3d_secure          -> "true"
         * 9. is_auth               -> "false"
         * 10. is_capture           -> "false"
         * 11. is_standalone_payment-> "true"
         * 12. is_voided            -> "false"
         * 13. order.id             -> "11223344"
         * 14. owner                -> "554433"
         * 15. pending              -> "false"
         * 16. source_data.pan      -> "4111"
         * 17. source_data.sub_type -> "Visa"
         * 18. source_data.type     -> "Card"
         * 19. success              -> "true"
         * 20. txn_response_code    -> "00"
         *
         * Concatenated String:
         * "500002026-08-08T14:00:00EGPfalsefalse998877665834828truefalsefalsetruefalse11223344554433false4111VisaCardtrue00"
         */
        String computedHmac = HmacVerifier.calculateHmac(validPayload, TEST_HMAC_SECRET);
        assertNotNull(computedHmac, "Calculated HMAC should not be null");

        boolean isValid = HmacVerifier.isValid(validPayload, computedHmac, TEST_HMAC_SECRET);
        assertTrue(isValid, "HMAC verification should return true for untampered payload");
    }

    @Test
    @DisplayName("Test 2: Tampered payload returns false")
    public void testTamperedPayloadFailsVerification() {
        String originalHmac = HmacVerifier.calculateHmac(validPayload, TEST_HMAC_SECRET);

        // Simulate tampering by altering amount_cents from 50000 to 500000
        Map<String, Object> tamperedPayload = new HashMap<>(validPayload);
        tamperedPayload.put("amount_cents", 500000);

        boolean isValid = HmacVerifier.isValid(tamperedPayload, originalHmac, TEST_HMAC_SECRET);
        assertFalse(isValid, "HMAC verification should return false for tampered payload");
    }

    @Test
    @DisplayName("Test 3: Missing required field returns false")
    public void testMissingRequiredFieldFailsVerification() {
        String originalHmac = HmacVerifier.calculateHmac(validPayload, TEST_HMAC_SECRET);

        // Remove required canonical field 'error_occured'
        Map<String, Object> incompletePayload = new HashMap<>(validPayload);
        incompletePayload.remove("error_occured");

        boolean isValid = HmacVerifier.isValid(incompletePayload, originalHmac, TEST_HMAC_SECRET);
        assertFalse(isValid, "HMAC verification should return false when a required field is missing");
    }
}
