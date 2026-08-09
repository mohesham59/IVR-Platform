package com.nexusivr.payment;

import com.nexusivr.payment.config.PaymobConfig;
import com.nexusivr.payment.dao.SubscriptionPlanDao;
import com.nexusivr.payment.dao.TransactionDao;
import com.nexusivr.payment.model.SubscriptionPlan;
import com.nexusivr.payment.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PaymentService orchestrator.
 */
public class PaymentServiceTest {

    @Mock
    private PaymobHttpClient paymobClient;

    @Mock
    private TransactionDao transactionDao;

    @Mock
    private SubscriptionPlanDao planDao;

    @Mock
    private PaymobConfig config;

    private PaymentService paymentService;
    private final String testHmacSecret = "test_secret_key_123";

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:payment_svc_test_db;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        com.nexusivr.payment.dao.DatabaseManager.setDataSourceForTesting(ds);

        // Pre-create schema for test
        try (java.sql.Connection conn = ds.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            try {
                stmt.execute("CREATE ALIAS gen_random_uuid FOR \"java.util.UUID.randomUUID\"");
            } catch (Exception ignored) {}
            stmt.execute("CREATE TABLE IF NOT EXISTS tenants (id UUID DEFAULT RANDOM_UUID() PRIMARY KEY, display_name VARCHAR(255), status VARCHAR(20) DEFAULT 'ACTIVE', subscription_plan_id UUID, subscription_status VARCHAR(20) DEFAULT 'INACTIVE', subscription_expires_at TIMESTAMP)");
            stmt.execute("CREATE TABLE IF NOT EXISTS subscription_plans (id UUID DEFAULT RANDOM_UUID() PRIMARY KEY, name VARCHAR(100), price_piasters BIGINT, billing_interval VARCHAR(20))");
            stmt.execute("CREATE TABLE IF NOT EXISTS transactions (id UUID DEFAULT RANDOM_UUID() PRIMARY KEY, tenant_id UUID, type VARCHAR(20), amount_piasters BIGINT, currency VARCHAR(10), status VARCHAR(20), paymob_transaction_id VARCHAR(100), paymob_order_id VARCHAR(100), plan_id UUID, card_token VARCHAR(255), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        }

        Map<String, String> testEnv = new HashMap<>();
        testEnv.put("PAYMOB_API_KEY", "dummy_api_key");
        testEnv.put("PAYMOB_SECRET_KEY", "dummy_secret_key");
        testEnv.put("PAYMOB_PUBLIC_KEY", "dummy_public_key");
        testEnv.put("PAYMOB_HMAC_SECRET", testHmacSecret);
        testEnv.put("PAYMOB_INTEGRATION_ID_CARD", "5834828");
        testEnv.put("PAYMOB_INTEGRATION_ID_WALLET", "5834829");
        testEnv.put("PAYMOB_MOTO_INTEGRATION_ID", "5834830");
        testEnv.put("PAYMOB_IFRAME_ID", "1067447");
        PaymobConfig.getInstance().initForTesting(testEnv);

        when(config.getHmacSecret()).thenReturn(testHmacSecret);
        when(config.getIntegrationIdCard()).thenReturn("5834828");

        paymentService = new PaymentService(paymobClient, transactionDao, planDao, config);
    }

    @Test
    @DisplayName("Test 1: initiateSubscriptionPayment creates PENDING transaction and returns checkout URL")
    public void testInitiateSubscriptionPayment() throws SQLException {
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(planId);
        plan.setName("Starter");
        plan.setPricePiasters(50000);
        plan.setBillingInterval("MONTHLY");

        when(planDao.findPlanById(planId)).thenReturn(plan);
        when(paymobClient.createIntention(anyLong(), anyString(), any(), any(), any()))
                .thenReturn("mock_client_secret_xyz");
        when(paymobClient.buildUnifiedCheckoutUrl("mock_client_secret_xyz"))
                .thenReturn("https://accept.paymob.com/unifiedcheckout/?publicKey=dummy_public_key&clientSecret=mock_client_secret_xyz");

        PaymentInitiationResult result = paymentService.initiateSubscriptionPayment(tenantId.toString(), planId.toString());

        assertNotNull(result);
        assertNotNull(result.getTransactionId());
        assertTrue(result.getCheckoutUrl().contains("clientSecret=mock_client_secret_xyz"));

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionDao, times(1)).createTransaction(captor.capture());
        Transaction createdTxn = captor.getValue();

        assertEquals(tenantId, createdTxn.getTenantId());
        assertEquals(planId, createdTxn.getPlanId());
        assertEquals(50000, createdTxn.getAmountPiasters());
        assertEquals("PENDING", createdTxn.getStatus());
        assertEquals("SUBSCRIPTION", createdTxn.getType());
    }

    @Test
    @DisplayName("Test 2: handleWebhookCallback with valid HMAC updates transaction status to SUCCESS")
    public void testHandleWebhookCallbackSuccess() throws SQLException {
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID localTxnId = UUID.randomUUID();

        Transaction pendingTxn = new Transaction();
        pendingTxn.setId(localTxnId);
        pendingTxn.setTenantId(tenantId);
        pendingTxn.setPlanId(planId);
        pendingTxn.setType("SUBSCRIPTION");
        pendingTxn.setStatus("PENDING");

        Map<String, Object> payload = createValidWebhookPayload(12345, 67890, tenantId.toString());
        String validHmac = HmacVerifier.calculateHmac(payload, testHmacSecret);

        when(transactionDao.findTransactionsByTenantId(tenantId)).thenReturn(List.of(pendingTxn));

        paymentService.handleWebhookCallback(payload, validHmac);

        verify(transactionDao, times(1)).updateTransactionStatus(eq(localTxnId), eq("SUCCESS"), anyString(), anyString());
    }

    @Test
    @DisplayName("Test 3: handleWebhookCallback with invalid HMAC rejects and does not touch database")
    public void testHandleWebhookCallbackInvalidHmac() throws SQLException {
        Map<String, Object> payload = createValidWebhookPayload(12345, 67890, UUID.randomUUID().toString());
        String invalidHmac = "invalid_tampered_hmac_12345";

        assertThrows(PaymobApiException.class, () -> paymentService.handleWebhookCallback(payload, invalidHmac));

        verify(transactionDao, never()).updateTransactionStatus(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Test 4: handleWebhookCallback called twice with same payload is idempotent (processes update only once)")
    public void testHandleWebhookCallbackIdempotency() throws SQLException {
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID localTxnId = UUID.randomUUID();

        Transaction completedTxn = new Transaction();
        completedTxn.setId(localTxnId);
        completedTxn.setTenantId(tenantId);
        completedTxn.setPlanId(planId);
        completedTxn.setType("SUBSCRIPTION");
        completedTxn.setStatus("SUCCESS");

        Map<String, Object> payload = createValidWebhookPayload(12345, 67890, tenantId.toString());
        String validHmac = HmacVerifier.calculateHmac(payload, testHmacSecret);

        when(transactionDao.findTransactionsByTenantId(tenantId)).thenReturn(List.of(completedTxn));

        // First call on an already completed transaction
        paymentService.handleWebhookCallback(payload, validHmac);

        // Verify database update was skipped due to idempotency
        verify(transactionDao, never()).updateTransactionStatus(any(), any(), any(), any());
    }

    private Map<String, Object> createValidWebhookPayload(long txnId, long orderId, String tenantIdStr) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("amount_cents", 50000);
        payload.put("created_at", "2026-08-08T15:00:00");
        payload.put("currency", "EGP");
        payload.put("error_occured", false); // Paymob canonical spelling with single 'r'
        payload.put("has_parent_transaction", false);
        payload.put("id", txnId);
        payload.put("integration_id", 5834828);
        payload.put("is_3d_secure", true);
        payload.put("is_auth", false);
        payload.put("is_capture", false);
        payload.put("is_standalone_payment", true);
        payload.put("is_voided", false);
        payload.put("order.id", orderId);
        payload.put("owner", 10001);
        payload.put("pending", false);
        payload.put("source_data.pan", "4111");
        payload.put("source_data.sub_type", "Visa");
        payload.put("source_data.type", "Card");
        payload.put("success", true);
        payload.put("txn_response_code", "00");

        Map<String, Object> extras = new HashMap<>();
        extras.put("tenantId", tenantIdStr);
        extras.put("transactionType", "SUBSCRIPTION");
        payload.put("extras", extras);

        return payload;
    }
}
