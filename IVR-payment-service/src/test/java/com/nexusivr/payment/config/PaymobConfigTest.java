package com.nexusivr.payment.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PaymobConfigTest {

    @BeforeEach
    void setUp() {
        PaymobConfig.resetInstance();
    }

    @Test
    void testEmptyPropsThrowsPaymobConfigException() {
        Map<String, String> emptyProps = new HashMap<>();
        PaymobConfigException ex = assertThrows(PaymobConfigException.class, () -> {
            PaymobConfig.initForTesting(emptyProps);
        });

        assertTrue(ex.getMessage().contains("Missing or unconfigured environment variables"));
        assertTrue(ex.getMessage().contains("PAYMOB_API_KEY"));
        assertTrue(ex.getMessage().contains("PAYMOB_SECRET_KEY"));
    }

    @Test
    void testPartialPropsThrowsPaymobConfigException() {
        Map<String, String> partialProps = new HashMap<>();
        partialProps.put(PaymobConfig.KEY_PAYMOB_API_KEY, "test_api_key");
        partialProps.put(PaymobConfig.KEY_PAYMOB_SECRET_KEY, "test_secret_key");
        // Missing remaining keys

        PaymobConfigException ex = assertThrows(PaymobConfigException.class, () -> {
            PaymobConfig.initForTesting(partialProps);
        });

        assertTrue(ex.getMessage().contains("PAYMOB_PUBLIC_KEY"));
        assertTrue(ex.getMessage().contains("PAYMOB_HMAC_SECRET"));
    }

    @Test
    void testPlaceholderPropsThrowsPaymobConfigException() {
        Map<String, String> placeholderProps = new HashMap<>();
        placeholderProps.put(PaymobConfig.KEY_PAYMOB_API_KEY, "your_api_key_here");
        placeholderProps.put(PaymobConfig.KEY_PAYMOB_SECRET_KEY, "your_secret_key_here");

        PaymobConfigException ex = assertThrows(PaymobConfigException.class, () -> {
            PaymobConfig.initForTesting(placeholderProps);
        });

        assertTrue(ex.getMessage().contains("PAYMOB_API_KEY"));
    }

    @Test
    void testValidConfigurationInitializesSuccessfully() {
        Map<String, String> validProps = new HashMap<>();
        validProps.put(PaymobConfig.KEY_PAYMOB_API_KEY, "egy_api_key_12345");
        validProps.put(PaymobConfig.KEY_PAYMOB_SECRET_KEY, "egy_secret_key_67890");
        validProps.put(PaymobConfig.KEY_PAYMOB_PUBLIC_KEY, "egy_public_key_abcde");
        validProps.put(PaymobConfig.KEY_PAYMOB_HMAC_SECRET, "egy_hmac_secret_fghij");
        validProps.put(PaymobConfig.KEY_PAYMOB_INTEGRATION_ID_CARD, "5834828");
        validProps.put(PaymobConfig.KEY_PAYMOB_INTEGRATION_ID_WALLET, "5834829");
        validProps.put(PaymobConfig.KEY_PAYMOB_MOTO_INTEGRATION_ID, "5834830");
        validProps.put(PaymobConfig.KEY_PAYMOB_IFRAME_ID, "1067447");
        validProps.put(PaymobConfig.KEY_PAYMENT_SERVICE_PORT, "8085");

        PaymobConfig config = assertDoesNotThrow(() -> PaymobConfig.initForTesting(validProps));

        assertNotNull(config);
        assertEquals("egy_api_key_12345", config.getApiKey());
        assertEquals("egy_secret_key_67890", config.getSecretKey());
        assertEquals("egy_public_key_abcde", config.getPublicKey());
        assertEquals("egy_hmac_secret_fghij", config.getHmacSecret());
        assertEquals("5834828", config.getCardIntegrationId());
        assertEquals("5834829", config.getWalletIntegrationId());
        assertEquals("5834830", config.getMotoIntegrationId());
        assertEquals("1067447", config.getIframeId());
        assertEquals(8085, config.getServicePort());
    }

    @Test
    void testAppServletContextListenerFailFast() {
        AppServletContextListener listener = new AppServletContextListener();
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            listener.contextInitialized(null);
        });

        assertTrue(ex.getMessage().contains("Service startup halted due to configuration errors"));
    }
}

