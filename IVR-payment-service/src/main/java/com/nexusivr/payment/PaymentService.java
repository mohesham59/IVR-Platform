package com.nexusivr.payment;

import com.nexusivr.payment.config.PaymobConfig;
import com.nexusivr.payment.dao.DatabaseManager;
import com.nexusivr.payment.dao.SubscriptionPlanDao;
import com.nexusivr.payment.dao.TransactionDao;
import com.nexusivr.payment.model.SubscriptionPlan;
import com.nexusivr.payment.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service orchestrating Paymob API calls, transaction persistence, and subscription management.
 */
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private final PaymobHttpClient paymobClient;
    private final TransactionDao transactionDao;
    private final SubscriptionPlanDao planDao;
    private final PaymobConfig config;

    public PaymentService() {
        this(new PaymobHttpClient(), new TransactionDao(), new SubscriptionPlanDao(), PaymobConfig.getInstance());
    }

    public PaymentService(PaymobHttpClient paymobClient, TransactionDao transactionDao, SubscriptionPlanDao planDao, PaymobConfig config) {
        this.paymobClient = paymobClient;
        this.transactionDao = transactionDao;
        this.planDao = planDao;
        this.config = config;
    }

    /**
     * Initiates a subscription payment by creating a Paymob Intention and persisting a PENDING Transaction.
     */
    public PaymentInitiationResult initiateSubscriptionPayment(String tenantIdStr, String planIdStr) throws SQLException {
        if (tenantIdStr == null || planIdStr == null) {
            throw new IllegalArgumentException("tenantId and planId cannot be null");
        }

        UUID tenantId = UUID.fromString(tenantIdStr);
        UUID planId = UUID.fromString(planIdStr);

        SubscriptionPlan plan = planDao.findPlanById(planId);
        if (plan == null) {
            throw new IllegalArgumentException("Subscription plan not found: " + planIdStr);
        }

        long amountPiasters = plan.getPricePiasters();
        List<Integer> integrationIds = getCardIntegrationIds();

        Map<String, Object> extras = new HashMap<>();
        extras.put("tenantId", tenantIdStr);
        extras.put("planId", planIdStr);
        extras.put("transactionType", "SUBSCRIPTION");

        BillingData billing = new BillingData("Tenant", "User", "tenant-" + tenantIdStr + "@nexusivr.com", "+201000000000");

        String clientSecret = paymobClient.createIntention(amountPiasters, "EGP", integrationIds, billing, extras);
        String checkoutUrl = paymobClient.buildUnifiedCheckoutUrl(clientSecret);

        Transaction txn = new Transaction();
        txn.setId(UUID.randomUUID());
        txn.setTenantId(tenantId);
        txn.setType("SUBSCRIPTION");
        txn.setAmountPiasters(amountPiasters);
        txn.setCurrency("EGP");
        txn.setStatus("PENDING");
        txn.setPlanId(planId);
        txn.setPaymobOrderId(clientSecret);

        transactionDao.createTransaction(txn);

        logger.info("Initiated subscription payment: TxnId={}, TenantId={}, PlanId={}, Amount={} piasters",
                txn.getId(), tenantId, planId, amountPiasters);

        return new PaymentInitiationResult(checkoutUrl, txn.getId());
    }

    /**
     * Initiates a one-time payment for a tenant.
     */
    public PaymentInitiationResult initiateOneTimePayment(String tenantIdStr, long amountPiasters, String description) throws SQLException {
        if (tenantIdStr == null) {
            throw new IllegalArgumentException("tenantId cannot be null");
        }
        if (amountPiasters <= 0) {
            throw new IllegalArgumentException("amountPiasters must be greater than 0");
        }

        UUID tenantId = UUID.fromString(tenantIdStr);
        List<Integer> integrationIds = getCardIntegrationIds();

        Map<String, Object> extras = new HashMap<>();
        extras.put("tenantId", tenantIdStr);
        extras.put("description", description != null ? description : "One-time Payment");
        extras.put("transactionType", "ONE_TIME");

        BillingData billing = new BillingData("Tenant", "User", "tenant-" + tenantIdStr + "@nexusivr.com", "+201000000000");

        String clientSecret = paymobClient.createIntention(amountPiasters, "EGP", integrationIds, billing, extras);
        String checkoutUrl = paymobClient.buildUnifiedCheckoutUrl(clientSecret);

        Transaction txn = new Transaction();
        txn.setId(UUID.randomUUID());
        txn.setTenantId(tenantId);
        txn.setType("ONE_TIME");
        txn.setAmountPiasters(amountPiasters);
        txn.setCurrency("EGP");
        txn.setStatus("PENDING");
        txn.setPaymobOrderId(clientSecret);

        transactionDao.createTransaction(txn);

        logger.info("Initiated one-time payment: TxnId={}, TenantId={}, Amount={} piasters",
                txn.getId(), tenantId, amountPiasters);

        return new PaymentInitiationResult(checkoutUrl, txn.getId());
    }

    /**
     * Processes Paymob webhook callback payload after verifying HMAC signature.
     */
    public void handleWebhookCallback(Map<String, Object> rawPayload, String receivedHmac) throws SQLException {
        if (!HmacVerifier.isValid(rawPayload, receivedHmac)) {
            Object txnIdObj = rawPayload != null ? rawPayload.get("id") : "unknown";
            logger.warn("Rejected webhook callback due to invalid HMAC. Paymob Txn ID: {}", txnIdObj);
            throw new PaymobApiException(401, "Invalid HMAC signature");
        }

        // Determine transaction outcome using Paymob canonical 'error_occured' field (single 'r')
        boolean errorOccured = parseBoolean(rawPayload.get("error_occured"));
        boolean success = parseBoolean(rawPayload.get("success"));
        String status = (!errorOccured && success) ? "SUCCESS" : "FAILED";

        String paymobTxnId = String.valueOf(rawPayload.get("id"));
        String paymobOrderId = extractOrderId(rawPayload);
        String cardToken = extractCardToken(rawPayload);

        Transaction txn = findMatchingTransaction(rawPayload, paymobOrderId, paymobTxnId);
        if (txn == null) {
            logger.warn("Webhook received for unknown transaction. Paymob Txn ID: {}, Order ID: {}", paymobTxnId, paymobOrderId);
            return;
        }

        // Idempotency check: if transaction is already terminal (SUCCESS or FAILED), do not reprocess
        if ("SUCCESS".equalsIgnoreCase(txn.getStatus()) || "FAILED".equalsIgnoreCase(txn.getStatus())) {
            logger.info("Duplicate webhook delivery ignored for Transaction ID: {}. Current status: {}", txn.getId(), txn.getStatus());
            return;
        }

        // Update transaction status
        transactionDao.updateTransactionStatus(txn.getId(), status, paymobTxnId, paymobOrderId);
        txn.setStatus(status);

        // Save card token if provided
        if (cardToken != null && !cardToken.isBlank()) {
            saveCardTokenForTransaction(txn.getId(), cardToken);
        }

        // If subscription payment succeeded, activate tenant subscription & extend expiration date
        if ("SUCCESS".equals(status) && "SUBSCRIPTION".equalsIgnoreCase(txn.getType())) {
            updateTenantSubscriptionOnSuccess(txn.getTenantId(), txn.getPlanId());
        }

        logger.info("Processed webhook callback for Transaction ID: {}. Updated Status: {}", txn.getId(), status);
    }

    /**
     * Fallback Verification: Directly queries Paymob API for transaction execution status
     * if the server-to-server webhook callback was un-received due to local dev/networking issues.
     */
    public Transaction verifyTransactionStatus(String transactionIdStr) throws SQLException {
        return verifyTransactionStatus(transactionIdStr, null);
    }

    public Transaction verifyTransactionStatus(String transactionIdStr, String inputPaymobTxnId) throws SQLException {
        if (transactionIdStr == null || transactionIdStr.isBlank()) {
            throw new IllegalArgumentException("transactionId cannot be null or empty");
        }
        UUID transactionId = UUID.fromString(transactionIdStr);
        Transaction txn = transactionDao.findTransactionById(transactionId);
        if (txn == null) {
            logger.warn("Transaction verification failed: No transaction found with ID {}", transactionIdStr);
            return null;
        }

        if (inputPaymobTxnId != null && !inputPaymobTxnId.isBlank() && txn.getPaymobTransactionId() == null) {
            txn.setPaymobTransactionId(inputPaymobTxnId);
            transactionDao.updateTransactionStatus(txn.getId(), txn.getStatus(), inputPaymobTxnId, txn.getPaymobOrderId());
        }

        if ("SUCCESS".equalsIgnoreCase(txn.getStatus()) || "FAILED".equalsIgnoreCase(txn.getStatus())) {
            return txn;
        }

        // Query Paymob for transaction inquiry
        com.google.gson.JsonObject inquiry = paymobClient.inquireTransaction(txn.getPaymobOrderId(), txn.getPaymobTransactionId());
        if (inquiry != null) {
            boolean isSuccess = inquiry.has("success") && inquiry.get("success").getAsBoolean();
            boolean errorOccured = inquiry.has("error_occured") && inquiry.get("error_occured").getAsBoolean();
            boolean isPending = inquiry.has("pending") && inquiry.get("pending").getAsBoolean();

            if (!errorOccured && isSuccess && !isPending) {
                String paymobTxnId = inquiry.has("id") ? inquiry.get("id").getAsString() : txn.getPaymobTransactionId();
                String paymobOrderId = txn.getPaymobOrderId();
                if (paymobOrderId == null && inquiry.has("order")) {
                    com.google.gson.JsonElement orderElem = inquiry.get("order");
                    if (orderElem.isJsonObject() && orderElem.getAsJsonObject().has("id")) {
                        paymobOrderId = orderElem.getAsJsonObject().get("id").getAsString();
                    } else if (orderElem.isJsonPrimitive()) {
                        paymobOrderId = orderElem.getAsString();
                    }
                }

                transactionDao.updateTransactionStatus(txn.getId(), "SUCCESS", paymobTxnId, paymobOrderId);
                txn.setStatus("SUCCESS");
                txn.setPaymobTransactionId(paymobTxnId);
                txn.setPaymobOrderId(paymobOrderId);

                if ("SUBSCRIPTION".equalsIgnoreCase(txn.getType()) && txn.getPlanId() != null) {
                    updateTenantSubscriptionOnSuccess(txn.getTenantId(), txn.getPlanId());
                }

                logger.info("Successfully verified and updated pending transaction ID {} to SUCCESS via Paymob Inquiry API.", txn.getId());
            } else if (!isPending && (errorOccured || !isSuccess)) {
                transactionDao.updateTransactionStatus(txn.getId(), "FAILED", txn.getPaymobTransactionId(), txn.getPaymobOrderId());
                txn.setStatus("FAILED");
                logger.info("Updated pending transaction ID {} to FAILED via Paymob Inquiry API.", txn.getId());
            }
        }
        return txn;
    }

    /**
     * One-time reconciliation step: matches stuck PENDING transactions in DB against recent Paymob transactions.
     */
    public int reconcileStuckTransactions() {
        int reconciledCount = 0;
        try {
            com.google.gson.JsonArray paymobTxns = paymobClient.listRecentTransactions(50);
            if (paymobTxns == null || paymobTxns.size() == 0) return 0;

            String sql = "SELECT id, tenant_id, type, amount_piasters, currency, status, paymob_transaction_id, paymob_order_id, plan_id, card_token, created_at, updated_at FROM transactions WHERE status = 'PENDING'";
            List<Transaction> pendingTxns = new ArrayList<>();
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    pendingTxns.add(mapRowToTxn(rs));
                }
            }

            for (Transaction dbTxn : pendingTxns) {
                for (com.google.gson.JsonElement elem : paymobTxns) {
                    if (!elem.isJsonObject()) continue;
                    com.google.gson.JsonObject pTxn = elem.getAsJsonObject();
                    boolean pSuccess = pTxn.has("success") && pTxn.get("success").getAsBoolean();
                    boolean pError = pTxn.has("error_occured") && pTxn.get("error_occured").getAsBoolean();
                    boolean pPending = pTxn.has("pending") && pTxn.get("pending").getAsBoolean();

                    if (!pSuccess || pError || pPending) continue;

                    long pAmount = pTxn.has("amount_cents") ? pTxn.get("amount_cents").getAsLong() : 0;
                    String pTxnId = pTxn.has("id") ? pTxn.get("id").getAsString() : null;
                    String pOrderId = null;
                    if (pTxn.has("order")) {
                        com.google.gson.JsonElement o = pTxn.get("order");
                        if (o.isJsonObject() && o.getAsJsonObject().has("id")) {
                            pOrderId = o.getAsJsonObject().get("id").getAsString();
                        } else if (o.isJsonPrimitive()) {
                            pOrderId = o.getAsString();
                        }
                    }

                    if (dbTxn.getAmountPiasters() == pAmount && pTxnId != null) {
                        transactionDao.updateTransactionStatus(dbTxn.getId(), "SUCCESS", pTxnId, pOrderId);
                        dbTxn.setStatus("SUCCESS");
                        dbTxn.setPaymobTransactionId(pTxnId);

                        if ("SUBSCRIPTION".equalsIgnoreCase(dbTxn.getType()) && dbTxn.getPlanId() != null) {
                            updateTenantSubscriptionOnSuccess(dbTxn.getTenantId(), dbTxn.getPlanId());
                        }

                        reconciledCount++;
                        logger.info("Reconciled PENDING transaction ID {} to SUCCESS with Paymob Txn ID {}", dbTxn.getId(), pTxnId);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error during transaction reconciliation: {}", e.getMessage(), e);
        }
        return reconciledCount;
    }

    /**
     * Automatically verifies and syncs all pending transactions for a given tenant.
     */
    public void autoVerifyPendingTransactions(UUID tenantId) {
        try {
            reconcileStuckTransactions();
            List<Transaction> transactions = transactionDao.findTransactionsByTenantId(tenantId);
            for (Transaction t : transactions) {
                if ("PENDING".equalsIgnoreCase(t.getStatus())) {
                    verifyTransactionStatus(t.getId().toString());
                }
            }
        } catch (Exception e) {
            logger.warn("Auto verification of pending transactions failed for tenant {}: {}", tenantId, e.getMessage());
        }
    }

    /**
     * Recurring subscription renewal process.
     */
    public void renewSubscriptionsDueToday() {
        logger.info("Starting automated subscription renewal check...");
        try (Connection conn = DatabaseManager.getConnection()) {
            if (conn == null) return;

            String sql = "SELECT t.id, t.subscription_plan_id, sp.price_piasters, sp.billing_interval " +
                         "FROM tenants t JOIN subscription_plans sp ON t.subscription_plan_id = sp.id " +
                         "WHERE t.subscription_status = 'ACTIVE' AND t.subscription_expires_at <= now()";

            List<TenantRenewalCandidate> candidates = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    candidates.add(new TenantRenewalCandidate(
                            (UUID) rs.getObject("id"),
                            (UUID) rs.getObject("subscription_plan_id"),
                            rs.getLong("price_piasters"),
                            rs.getString("billing_interval")
                    ));
                }
            }

            for (TenantRenewalCandidate candidate : candidates) {
                renewSingleSubscription(candidate);
            }
        } catch (Exception e) {
            logger.error("Error during automated subscription renewal run: {}", e.getMessage(), e);
        }
    }

    private void renewSingleSubscription(TenantRenewalCandidate candidate) {
        try {
            String cardToken = findLastCardTokenForTenant(candidate.tenantId);
            if (cardToken == null || cardToken.isBlank()) {
                logger.warn("Cannot renew subscription for tenant {}: No stored card token found.", candidate.tenantId);
                markTenantSubscriptionExpired(candidate.tenantId);
                return;
            }

            String paymobResult = paymobClient.chargeSavedCardBackend(cardToken, candidate.pricePiasters);
            
            Transaction txn = new Transaction();
            txn.setId(UUID.randomUUID());
            txn.setTenantId(candidate.tenantId);
            txn.setType("SUBSCRIPTION");
            txn.setAmountPiasters(candidate.pricePiasters);
            txn.setCurrency("EGP");
            txn.setStatus("SUCCESS");
            txn.setPlanId(candidate.planId);
            txn.setCardToken(cardToken);
            transactionDao.createTransaction(txn);

            updateTenantSubscriptionOnSuccess(candidate.tenantId, candidate.planId);
            logger.info("Successfully renewed subscription for tenant {}", candidate.tenantId);

        } catch (Exception e) {
            logger.error("Failed to renew subscription for tenant {}: {}", candidate.tenantId, e.getMessage());
            try {
                Transaction failedTxn = new Transaction();
                failedTxn.setId(UUID.randomUUID());
                failedTxn.setTenantId(candidate.tenantId);
                failedTxn.setType("SUBSCRIPTION");
                failedTxn.setAmountPiasters(candidate.pricePiasters);
                failedTxn.setCurrency("EGP");
                failedTxn.setStatus("FAILED");
                failedTxn.setPlanId(candidate.planId);
                transactionDao.createTransaction(failedTxn);
                
                markTenantSubscriptionExpired(candidate.tenantId);
            } catch (SQLException ex) {
                logger.error("Error logging failed renewal transaction for tenant {}: {}", candidate.tenantId, ex.getMessage());
            }
        }
    }

    private void updateTenantSubscriptionOnSuccess(UUID tenantId, UUID planId) throws SQLException {
        SubscriptionPlan plan = planDao.findPlanById(planId);
        int daysToAdd = (plan != null && "YEARLY".equalsIgnoreCase(plan.getBillingInterval())) ? 365 : 30;

        Instant expiresAt = Instant.now().plus(daysToAdd, ChronoUnit.DAYS);

        String sql = "UPDATE tenants SET subscription_plan_id = ?, subscription_status = 'ACTIVE', " +
                     "subscription_expires_at = ? WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, planId);
            stmt.setTimestamp(2, Timestamp.from(expiresAt));
            stmt.setObject(3, tenantId);
            stmt.executeUpdate();
        }
        logger.info("Updated tenant {} subscription to ACTIVE until {}", tenantId, expiresAt);
    }

    private void markTenantSubscriptionExpired(UUID tenantId) {
        String sql = "UPDATE tenants SET subscription_status = 'EXPIRED' WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, tenantId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error setting tenant {} status to EXPIRED: {}", tenantId, e.getMessage());
        }
    }

    private String findLastCardTokenForTenant(UUID tenantId) throws SQLException {
        String sql = "SELECT card_token FROM transactions WHERE tenant_id = ? AND card_token IS NOT NULL AND status = 'SUCCESS' ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, tenantId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("card_token");
                }
            }
        }
        return null;
    }

    private void saveCardTokenForTransaction(UUID transactionId, String cardToken) throws SQLException {
        String sql = "UPDATE transactions SET card_token = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, cardToken);
            stmt.setObject(2, transactionId);
            stmt.executeUpdate();
        }
    }

    private Transaction findMatchingTransaction(Map<String, Object> rawPayload, String paymobOrderId, String paymobTxnId) throws SQLException {
        // Try searching by paymob_order_id or paymob_transaction_id
        if (paymobOrderId != null && !paymobOrderId.isBlank()) {
            String sql = "SELECT id, tenant_id, type, amount_piasters, currency, status, paymob_transaction_id, paymob_order_id, plan_id, card_token, created_at, updated_at FROM transactions WHERE paymob_order_id = ? OR paymob_transaction_id = ?";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, paymobOrderId);
                stmt.setString(2, paymobTxnId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return mapRowToTxn(rs);
                    }
                }
            }
        }

        // Fallback: search for PENDING transaction matching tenant ID if provided in extras
        Object extrasObj = rawPayload.get("extras");
        if (extrasObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> extras = (Map<String, Object>) extrasObj;
            Object tenantIdObj = extras.get("tenantId");
            if (tenantIdObj != null) {
                try {
                    UUID tenantId = UUID.fromString(tenantIdObj.toString());
                    List<Transaction> pendingList = transactionDao.findTransactionsByTenantId(tenantId);
                    for (Transaction t : pendingList) {
                        if ("PENDING".equalsIgnoreCase(t.getStatus())) {
                            return t;
                        }
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return null;
    }

    private Transaction mapRowToTxn(ResultSet rs) throws SQLException {
        Transaction txn = new Transaction();
        txn.setId((UUID) rs.getObject("id"));
        txn.setTenantId((UUID) rs.getObject("tenant_id"));
        txn.setType(rs.getString("type"));
        txn.setAmountPiasters(rs.getLong("amount_piasters"));
        txn.setCurrency(rs.getString("currency"));
        txn.setStatus(rs.getString("status"));
        txn.setPaymobTransactionId(rs.getString("paymob_transaction_id"));
        txn.setPaymobOrderId(rs.getString("paymob_order_id"));
        txn.setPlanId((UUID) rs.getObject("plan_id"));
        txn.setCardToken(rs.getString("card_token"));
        return txn;
    }

    private List<Integer> getCardIntegrationIds() {
        try {
            return List.of(Integer.parseInt(config.getIntegrationIdCard()));
        } catch (Exception e) {
            return List.of(5834828);
        }
    }

    private String extractOrderId(Map<String, Object> payload) {
        Object orderObj = payload.get("order");
        if (orderObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> orderMap = (Map<String, Object>) orderObj;
            return String.valueOf(orderMap.get("id"));
        }
        if (payload.containsKey("order.id")) {
            return String.valueOf(payload.get("order.id"));
        }
        return null;
    }

    private String extractCardToken(Map<String, Object> payload) {
        if (payload.containsKey("token")) return String.valueOf(payload.get("token"));
        if (payload.containsKey("card_token")) return String.valueOf(payload.get("card_token"));
        Object tokenObj = payload.get("token_detail");
        if (tokenObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) tokenObj;
            if (map.containsKey("token")) return String.valueOf(map.get("token"));
        }
        return null;
    }

    private boolean parseBoolean(Object val) {
        if (val instanceof Boolean) return (Boolean) val;
        if (val != null) return "true".equalsIgnoreCase(val.toString().trim());
        return false;
    }

    /**
     * Expires all PENDING transactions older than 45 minutes.
     * Called by the scheduler every 5 minutes to clean up abandoned/timed-out checkouts.
     */
    public void expireStalePendingTransactions() {
        try {
            int expired = transactionDao.expireStalePendingTransactions(45);
            if (expired > 0) {
                logger.info("Stale-PENDING cleanup: expired {} transaction(s) older than 45 minutes.", expired);
            } else {
                logger.debug("Stale-PENDING cleanup: no stale transactions found.");
            }
        } catch (Exception e) {
            logger.error("Error expiring stale PENDING transactions: {}", e.getMessage(), e);
        }
    }

    /**
     * Cancels a specific PENDING transaction if it belongs to the requesting tenant.
     * Returns the updated transaction, or null if not found / not PENDING / wrong tenant.
     */
    public Transaction cancelTransaction(String transactionIdStr, String tenantIdStr) throws Exception {
        UUID transactionId = UUID.fromString(transactionIdStr);
        UUID tenantId = UUID.fromString(tenantIdStr);

        Transaction existing = transactionDao.findTransactionById(transactionId);
        if (existing == null) {
            throw new IllegalArgumentException("Transaction not found: " + transactionIdStr);
        }
        if (!tenantId.equals(existing.getTenantId())) {
            throw new SecurityException("Transaction does not belong to tenant " + tenantIdStr);
        }
        if (!"PENDING".equalsIgnoreCase(existing.getStatus())) {
            throw new IllegalStateException("Only PENDING transactions can be cancelled. Current status: " + existing.getStatus());
        }

        boolean cancelled = transactionDao.cancelTransaction(transactionId, tenantId);
        if (!cancelled) {
            throw new IllegalStateException("Failed to cancel transaction — may have already been processed.");
        }

        existing.setStatus("CANCELLED");
        logger.info("Transaction {} cancelled by tenant {}", transactionId, tenantId);
        return existing;
    }

    private record TenantRenewalCandidate(UUID tenantId, UUID planId, long pricePiasters, String billingInterval) {}
}
