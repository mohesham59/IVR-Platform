package com.nexusivr.payment.controller;

import com.google.gson.JsonObject;

import com.nexusivr.payment.PaymentInitiationResult;
import com.nexusivr.payment.PaymentService;
import com.nexusivr.payment.dao.DatabaseManager;
import com.nexusivr.payment.dao.SubscriptionPlanDao;
import com.nexusivr.payment.dao.TransactionDao;
import com.nexusivr.payment.model.SubscriptionPlan;
import com.nexusivr.payment.model.Transaction;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Servlet handling payment initiation, webhook callbacks, transaction history, platform summaries, and subscription plan management.
 */
@WebServlet(urlPatterns = {
        "/api/payments/subscription/initiate",
        "/api/payments/onetime/initiate",
        "/api/payments/webhook",
        "/api/payments/verify",
        "/api/payments/reconcile",
        "/api/payments/cancel",
        "/api/payments/history",
        "/api/payments/summary",
        "/api/payments/plans"
})
public class PaymentServlet extends BasePaymentServlet {

    private final PaymentService paymentService;
    private final TransactionDao transactionDao;
    private final SubscriptionPlanDao planDao;

    public PaymentServlet() {
        this(new PaymentService(), new TransactionDao(), new SubscriptionPlanDao());
    }

    public PaymentServlet(PaymentService paymentService, TransactionDao transactionDao, SubscriptionPlanDao planDao) {
        this.paymentService = paymentService;
        this.transactionDao = transactionDao;
        this.planDao = planDao;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getServletPath();
        try {
            switch (path) {
                case "/api/payments/subscription/initiate" -> handleSubscriptionInitiate(req, resp);
                case "/api/payments/onetime/initiate" -> handleOneTimeInitiate(req, resp);
                case "/api/payments/webhook" -> handleWebhook(req, resp);
                case "/api/payments/verify" -> handleVerifyTransaction(req, resp);
                case "/api/payments/reconcile" -> handleReconcile(req, resp);
                case "/api/payments/cancel" -> handleCancelTransaction(req, resp);
                case "/api/payments/plans" -> handleCreatePlan(req, resp);
                default -> resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getServletPath();
        try {
            switch (path) {
                case "/api/payments/history" -> handleGetHistory(req, resp);
                case "/api/payments/summary" -> handleGetSummary(req, resp);
                case "/api/payments/plans" -> handleGetPlans(req, resp);
                case "/api/payments/verify" -> handleVerifyTransaction(req, resp);
                default -> resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getServletPath();
        try {
            if ("/api/payments/plans".equals(path)) {
                handleUpdatePlan(req, resp);
            } else {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getServletPath();
        try {
            if ("/api/payments/plans".equals(path)) {
                handleDeletePlan(req, resp);
            } else {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    private void handleSubscriptionInitiate(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String authenticatedTenantId = verifyTenantAuth(req, resp);
        if (authenticatedTenantId == null) return;

        JsonObject json = parseRequestBody(req, JsonObject.class);
        if (json == null || !json.has("planId")) {
            sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST, Map.of("error", "Missing required field 'planId'"));
            return;
        }

        String tenantId = json.has("tenantId") ? json.get("tenantId").getAsString() : authenticatedTenantId;
        String planId = json.get("planId").getAsString();

        PaymentInitiationResult result = paymentService.initiateSubscriptionPayment(tenantId, planId);

        Map<String, Object> respMap = new HashMap<>();
        respMap.put("success", true);
        respMap.put("checkoutUrl", result.getCheckoutUrl());
        respMap.put("transactionId", result.getTransactionId().toString());

        sendJsonResponse(resp, HttpServletResponse.SC_OK, respMap);
    }

    private void handleOneTimeInitiate(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String authenticatedTenantId = verifyTenantAuth(req, resp);
        if (authenticatedTenantId == null) return;

        JsonObject json = parseRequestBody(req, JsonObject.class);
        if (json == null || !json.has("amountPiasters")) {
            sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST, Map.of("error", "Missing required field 'amountPiasters'"));
            return;
        }

        String tenantId = json.has("tenantId") ? json.get("tenantId").getAsString() : authenticatedTenantId;
        long amountPiasters = json.get("amountPiasters").getAsLong();
        String description = json.has("description") ? json.get("description").getAsString() : "One-time Payment";

        PaymentInitiationResult result = paymentService.initiateOneTimePayment(tenantId, amountPiasters, description);

        Map<String, Object> respMap = new HashMap<>();
        respMap.put("success", true);
        respMap.put("checkoutUrl", result.getCheckoutUrl());
        respMap.put("transactionId", result.getTransactionId().toString());

        sendJsonResponse(resp, HttpServletResponse.SC_OK, respMap);
    }

    private void handleWebhook(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String hmac = req.getParameter("hmac");
        if (hmac == null || hmac.isBlank()) {
            hmac = req.getHeader("X-Paymob-HMAC");
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        String body = sb.toString();

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = gson.fromJson(body, Map.class);
        if (payload != null && payload.containsKey("obj")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> objMap = (Map<String, Object>) payload.get("obj");
            payload = objMap;
        }

        paymentService.handleWebhookCallback(payload, hmac);

        sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of("status", "SUCCESS", "message", "Webhook processed successfully"));
    }

    private void handleVerifyTransaction(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String transactionId = req.getParameter("transactionId");
        String paymobTxnId = req.getParameter("paymobTransactionId");
        if (transactionId == null || transactionId.isBlank()) {
            JsonObject json = parseRequestBody(req, JsonObject.class);
            if (json != null) {
                if (json.has("transactionId")) {
                    transactionId = json.get("transactionId").getAsString();
                }
                if (paymobTxnId == null && json.has("paymobTransactionId")) {
                    paymobTxnId = json.get("paymobTransactionId").getAsString();
                }
            }
        }

        if (transactionId == null || transactionId.isBlank()) {
            sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST, Map.of("error", "Missing required parameter 'transactionId'"));
            return;
        }

        Transaction verifiedTxn = paymentService.verifyTransactionStatus(transactionId, paymobTxnId);
        if (verifiedTxn != null) {
            sendJsonResponse(resp, HttpServletResponse.SC_OK, verifiedTxn);
        } else {
            sendJsonResponse(resp, HttpServletResponse.SC_NOT_FOUND, Map.of("error", "Transaction not found"));
        }
    }

    private void handleReconcile(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        if (!verifySuperAdminAuth(req, resp)) return;
        int reconciled = paymentService.reconcileStuckTransactions();
        sendJsonResponse(resp, HttpServletResponse.SC_OK,
                Map.of("success", true, "reconciledCount", reconciled,
                        "message", reconciled + " stuck PENDING transaction(s) resolved to SUCCESS."));
    }

    private void handleCancelTransaction(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String tenantId = verifyTenantAuth(req, resp);
        if (tenantId == null) return;

        JsonObject json = parseRequestBody(req, JsonObject.class);
        if (json == null || !json.has("transactionId")) {
            sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST, Map.of("error", "Missing required field 'transactionId'"));
            return;
        }

        String transactionId = json.get("transactionId").getAsString();
        try {
            Transaction cancelled = paymentService.cancelTransaction(transactionId, tenantId);
            sendJsonResponse(resp, HttpServletResponse.SC_OK,
                    Map.of("success", true, "message", "Transaction cancelled successfully", "transaction", cancelled));
        } catch (IllegalArgumentException e) {
            sendJsonResponse(resp, HttpServletResponse.SC_NOT_FOUND, Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            sendJsonResponse(resp, HttpServletResponse.SC_FORBIDDEN, Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            sendJsonResponse(resp, HttpServletResponse.SC_CONFLICT, Map.of("error", e.getMessage()));
        }
    }

    private void handleGetHistory(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        // If superadmin, return all transactions across tenants
        if (isSuperAdmin(req)) {
            List<Transaction> transactions = transactionDao.findAllTransactions();
            sendJsonResponse(resp, HttpServletResponse.SC_OK, transactions);
            return;
        }
        // Otherwise require tenant authentication (auth header, X‑Tenant‑ID header or query param)
        String tenantIdStr = verifyTenantAuth(req, resp);
        if (tenantIdStr == null) return;

        UUID tenantId = UUID.fromString(tenantIdStr);
        logger.debug("Fetching payment history for tenant {}", tenantId);
        // Ensure any pending transactions are reconciled before returning history
        paymentService.autoVerifyPendingTransactions(tenantId);
        List<Transaction> transactions = transactionDao.findTransactionsByTenantId(tenantId);
        logger.debug("Found {} transactions for tenant {}", transactions.size(), tenantId);
        // Fallback: if DAO returned no rows, attempt a broader query as a safety net
        if (transactions.isEmpty()) {
            logger.warn("No transactions returned for tenant {} via findTransactionsByTenantId – attempting fallback query");
            List<Transaction> all = transactionDao.findAllTransactions();
            for (Transaction t : all) {
                if (tenantId.equals(t.getTenantId())) {
                    transactions.add(t);
                }
            }
            logger.debug("Fallback query yielded {} transactions for tenant {}", transactions.size(), tenantId);
        }
        sendJsonResponse(resp, HttpServletResponse.SC_OK, transactions);
    }

    private void handleGetSummary(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        if (!verifySuperAdminAuth(req, resp)) return;

        long totalRevenuePiastersThisMonth = 0;
        int activeSubscriptions = 0;
        int failedPaymentsCount = 0;

        try (Connection conn = DatabaseManager.getConnection()) {
            if (conn != null) {
                String revSql = "SELECT COALESCE(SUM(amount_piasters), 0) FROM transactions WHERE status = 'SUCCESS' AND created_at >= date_trunc('month', now())";
                try (PreparedStatement stmt = conn.prepareStatement(revSql);
                     ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) totalRevenuePiastersThisMonth = rs.getLong(1);
                }

                String subSql = "SELECT COUNT(*) FROM tenants WHERE subscription_status = 'ACTIVE'";
                try (PreparedStatement stmt = conn.prepareStatement(subSql);
                     ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) activeSubscriptions = rs.getInt(1);
                }

                String failSql = "SELECT COUNT(*) FROM transactions WHERE status = 'FAILED'";
                try (PreparedStatement stmt = conn.prepareStatement(failSql);
                     ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) failedPaymentsCount = rs.getInt(1);
                }
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalRevenuePiastersThisMonth", totalRevenuePiastersThisMonth);
        summary.put("activeSubscriptions", activeSubscriptions);
        summary.put("failedPaymentsCount", failedPaymentsCount);

        sendJsonResponse(resp, HttpServletResponse.SC_OK, summary);
    }

    private void handleGetPlans(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        List<SubscriptionPlan> plans = planDao.findAllPlans();
        sendJsonResponse(resp, HttpServletResponse.SC_OK, plans);
    }

    private void handleCreatePlan(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        if (!verifySuperAdminAuth(req, resp)) return;

        SubscriptionPlan plan = parseRequestBody(req, SubscriptionPlan.class);
        if (plan == null || plan.getName() == null || plan.getPricePiasters() <= 0) {
            sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST, Map.of("error", "Invalid subscription plan data"));
            return;
        }

        SubscriptionPlan created = planDao.createPlan(plan);
        sendJsonResponse(resp, HttpServletResponse.SC_CREATED, created);
    }

    private void handleUpdatePlan(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        if (!verifySuperAdminAuth(req, resp)) return;

        SubscriptionPlan plan = parseRequestBody(req, SubscriptionPlan.class);
        if (plan == null || plan.getId() == null) {
            sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST, Map.of("error", "Plan ID is required for update"));
            return;
        }

        boolean updated = planDao.updatePlan(plan);
        if (updated) {
            sendJsonResponse(resp, HttpServletResponse.SC_OK, planDao.findPlanById(plan.getId()));
        } else {
            sendJsonResponse(resp, HttpServletResponse.SC_NOT_FOUND, Map.of("error", "Subscription plan not found"));
        }
    }

    private void handleDeletePlan(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        if (!verifySuperAdminAuth(req, resp)) return;

        String idStr = req.getParameter("id");
        if (idStr == null || idStr.isBlank()) {
            sendJsonResponse(resp, HttpServletResponse.SC_BAD_REQUEST, Map.of("error", "Missing query parameter 'id'"));
            return;
        }

        UUID id = UUID.fromString(idStr);
        boolean deleted = planDao.deletePlan(id);
        if (deleted) {
            sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of("success", true, "message", "Plan deleted successfully"));
        } else {
            sendJsonResponse(resp, HttpServletResponse.SC_NOT_FOUND, Map.of("error", "Subscription plan not found"));
        }
    }
}
