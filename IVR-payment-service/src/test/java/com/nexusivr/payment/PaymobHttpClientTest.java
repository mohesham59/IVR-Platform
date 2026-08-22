package com.nexusivr.payment;

import com.nexusivr.payment.config.PaymobConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for PaymobHttpClient URL construction and validation.
 */
public class PaymobHttpClientTest {

    private PaymobHttpClient client;

    @BeforeEach
    public void setUp() {
        PaymobConfig config = PaymobConfig.getInstance();
        java.util.Map<String, String> testEnv = new java.util.HashMap<>();
        testEnv.put("PAYMOB_API_KEY", "dummy_api_key");
        testEnv.put("PAYMOB_SECRET_KEY", "dummy_secret_key");
        testEnv.put("PAYMOB_PUBLIC_KEY", "dummy_public_key_123");
        testEnv.put("PAYMOB_HMAC_SECRET", "dummy_hmac_secret");
        testEnv.put("PAYMOB_INTEGRATION_ID_CARD", "5834828");
        testEnv.put("PAYMOB_INTEGRATION_ID_WALLET", "5834829");
        testEnv.put("PAYMOB_MOTO_INTEGRATION_ID", "5834830");
        testEnv.put("PAYMOB_IFRAME_ID", "1067447");

        config.initForTesting(testEnv);
        client = new PaymobHttpClient(config, java.net.http.HttpClient.newHttpClient());
    }

    @Test
    @DisplayName("Build unified checkout URL includes public key and client secret correctly")
    public void testBuildUnifiedCheckoutUrl() {
        String clientSecret = "secret_abc_123_xyz";
        String url = client.buildUnifiedCheckoutUrl(clientSecret);

        assertNotNull(url);
        assertTrue(url.startsWith("https://accept.paymob.com/unifiedcheckout/?"));
        assertTrue(url.contains("publicKey=dummy_public_key_123"));
        assertTrue(url.contains("clientSecret=secret_abc_123_xyz"));
    }

    @Test
    @DisplayName("Build unified checkout URL throws IllegalArgumentException on empty secret")
    public void testBuildUnifiedCheckoutUrlInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> client.buildUnifiedCheckoutUrl(null));
        assertThrows(IllegalArgumentException.class, () -> client.buildUnifiedCheckoutUrl("   "));
    }
}
