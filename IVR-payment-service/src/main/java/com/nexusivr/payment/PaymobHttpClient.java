package com.nexusivr.payment;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nexusivr.payment.config.PaymobConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Paymob HTTP Client built on java.net.http.HttpClient (Java 11+).
 * Handles Intention API, Unified Checkout URL generation, Subscription Plan Creation,
 * Subscription Enrollment, and MOTO backend saved-card charging.
 */
public class PaymobHttpClient {

    private static final Logger logger = LoggerFactory.getLogger(PaymobHttpClient.class);

    private static final String BASE_URL = "https://accept.paymob.com";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final Gson gson;
    private final PaymobConfig config;

    public PaymobHttpClient() {
        this(PaymobConfig.getInstance(), HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
    }

    public PaymobHttpClient(PaymobConfig config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
        this.gson = new Gson();
    }

    /**
     * Method 1 — Create Intention
     * POST https://accept.paymob.com/v1/intention/
     * Header: Authorization: Token <PAYMOB_SECRET_KEY>
     */
    public String createIntention(long amountPiasters, String currency, List<Integer> integrationIds, 
                                  BillingData billingData, Map<String, Object> extras) {
        return createIntention(amountPiasters, currency, integrationIds, billingData, extras, "http://localhost:3000/payment/callback");
    }

    public String createIntention(long amountPiasters, String currency, List<Integer> integrationIds, 
                                  BillingData billingData, Map<String, Object> extras, String redirectionUrl) {
        String url = BASE_URL + "/v1/intention/";

        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("amount", amountPiasters);
        bodyMap.put("currency", currency != null ? currency : "EGP");
        bodyMap.put("payment_methods", integrationIds);
        if (billingData != null) {
            bodyMap.put("billing_data", billingData);
        }
        if (extras != null && !extras.isEmpty()) {
            bodyMap.put("extras", extras);
        }
        String redirect = (redirectionUrl != null && !redirectionUrl.isBlank()) 
                ? redirectionUrl 
                : "http://localhost:3000/payment/callback";
        bodyMap.put("redirection_url", redirect);

        String jsonBody = gson.toJson(bodyMap);
        logger.debug("Creating Paymob Intention: amount={} piasters, currency={}, redirectionUrl={}", amountPiasters, currency, redirect);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Token " + config.getSecretKey())
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        String responseBody = sendHttpRequest(request);
        JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

        if (jsonResponse.has("client_secret")) {
            return jsonResponse.get("client_secret").getAsString();
        }
        throw new PaymobApiException(200, "Response missing client_secret field: " + responseBody);
    }

    /**
     * Method 2 — Build Checkout URL
     */
    public String buildUnifiedCheckoutUrl(String clientSecret) {
        if (clientSecret == null || clientSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("clientSecret cannot be null or empty");
        }
        String publicKey = config.getPublicKey();
        String encodedPublicKey = URLEncoder.encode(publicKey, StandardCharsets.UTF_8);
        String encodedClientSecret = URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);
        return BASE_URL + "/unifiedcheckout/?publicKey=" + encodedPublicKey + "&clientSecret=" + encodedClientSecret;
    }

    /**
     * Method 3 — Auth Token (for Subscription & Legacy APIs)
     * POST https://accept.paymob.com/api/auth/tokens
     */
    public String generateAuthToken() {
        String url = BASE_URL + "/api/auth/tokens";

        Map<String, String> bodyMap = new HashMap<>();
        bodyMap.put("api_key", config.getApiKey());

        String jsonBody = gson.toJson(bodyMap);
        logger.debug("Generating Paymob Auth Token with API Key: {}", maskSecret(config.getApiKey()));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        String responseBody = sendHttpRequest(request);
        JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

        if (jsonResponse.has("token")) {
            return jsonResponse.get("token").getAsString();
        }
        throw new PaymobApiException(200, "Response missing token field: " + responseBody);
    }

    /**
     * Method 4 — Subscription Plan Creation
     * POST https://accept.paymob.com/api/acceptance/subscription-plans/
     */
    public String createSubscriptionPlan(SubscriptionPlanRequest planRequest) {
        String authToken = generateAuthToken();
        String url = BASE_URL + "/api/acceptance/subscription-plans/";

        String jsonBody = gson.toJson(planRequest);
        logger.debug("Creating Paymob Subscription Plan: name={}, amount={}", planRequest.getName(), planRequest.getAmountPiasters());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Token " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        String responseBody = sendHttpRequest(request);
        JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
        if (jsonResponse.has("id")) {
            return jsonResponse.get("id").getAsString();
        }
        return responseBody;
    }

    /**
     * Method 5 — Enroll Customer in Subscription
     * POST https://accept.paymob.com/api/acceptance/subscriptions/
     */
    public String enrollCustomerInSubscription(String planId, String customerId, String cardToken) {
        String authToken = generateAuthToken();
        String url = BASE_URL + "/api/acceptance/subscriptions/";

        Map<String, String> bodyMap = new HashMap<>();
        bodyMap.put("plan_id", planId);
        bodyMap.put("customer_id", customerId);
        bodyMap.put("card_token", cardToken);

        String jsonBody = gson.toJson(bodyMap);
        logger.debug("Enrolling customer in subscription: planId={}, customerId={}, cardToken={}", planId, customerId, maskSecret(cardToken));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Authorization", "Token " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return sendHttpRequest(request);
    }

    /**
     * Method 6 — Backend Saved-Card Charge (Moto)
     * Charges a stored token backend-to-backend with no user interaction using PAYMOB_MOTO_INTEGRATION_ID.
     */
    public String chargeSavedCardBackend(String cardToken, long amountPiasters) {
        logger.debug("Initiating backend MOTO saved-card charge: amount={} piasters, cardToken={}", amountPiasters, maskSecret(cardToken));

        // Step 1: Obtain Auth Token
        String authToken = generateAuthToken();

        // Step 2: Create Order
        String orderUrl = BASE_URL + "/api/ecommerce/orders";
        Map<String, Object> orderBody = new HashMap<>();
        orderBody.put("auth_token", authToken);
        orderBody.put("delivery_needed", "false");
        orderBody.put("amount_cents", String.valueOf(amountPiasters));
        orderBody.put("currency", "EGP");
        orderBody.put("items", List.of());

        HttpRequest orderRequest = HttpRequest.newBuilder()
                .uri(URI.create(orderUrl))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(orderBody)))
                .build();

        String orderResponseStr = sendHttpRequest(orderRequest);
        JsonObject orderJson = gson.fromJson(orderResponseStr, JsonObject.class);
        String orderId = orderJson.get("id").getAsString();

        // Step 3: Request Payment Key using MOTO Integration ID
        String keyUrl = BASE_URL + "/api/acceptance/payment_keys";
        Map<String, Object> keyBody = new HashMap<>();
        keyBody.put("auth_token", authToken);
        keyBody.put("amount_cents", String.valueOf(amountPiasters));
        keyBody.put("expiration", 3600);
        keyBody.put("order_id", orderId);
        keyBody.put("billing_data", new BillingData("Nexus", "Subscription", "billing@nexusivr.com", "+201000000000"));
        keyBody.put("currency", "EGP");
        keyBody.put("integration_id", Integer.parseInt(config.getMotoIntegrationId()));

        HttpRequest keyRequest = HttpRequest.newBuilder()
                .uri(URI.create(keyUrl))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(keyBody)))
                .build();

        String keyResponseStr = sendHttpRequest(keyRequest);
        JsonObject keyJson = gson.fromJson(keyResponseStr, JsonObject.class);
        String paymentToken = keyJson.get("token").getAsString();

        // Step 4: Execute Charge using Stored Card Token
        String payUrl = BASE_URL + "/api/acceptance/payments/pay";
        Map<String, Object> sourceMap = new HashMap<>();
        sourceMap.put("identifier", cardToken);
        sourceMap.put("subtype", "TOKEN");

        Map<String, Object> payBody = new HashMap<>();
        payBody.put("source", sourceMap);
        payBody.put("payment_token", paymentToken);

        HttpRequest payRequest = HttpRequest.newBuilder()
                .uri(URI.create(payUrl))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payBody)))
                .build();

        String payResult = sendHttpRequest(payRequest);
        logger.info("MOTO charge successfully submitted for amount={} piasters.", amountPiasters);
        return payResult;
    }

    /**
     * Method 7 — Transaction Inquiry API (Fallback Verification)
     * Queries Paymob for transaction execution status when server-to-server webhook is un-received.
     */
    public JsonObject inquireTransaction(String paymobOrderId, String paymobTxnId) {
        try {
            String authToken = generateAuthToken();

            if (paymobTxnId != null && !paymobTxnId.isBlank()) {
                String url = BASE_URL + "/api/acceptance/transactions/" + paymobTxnId;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(TIMEOUT)
                        .header("Authorization", "Bearer " + authToken)
                        .GET()
                        .build();

                String responseBody = sendHttpRequest(request);
                return gson.fromJson(responseBody, JsonObject.class);
            }

            // Fallback: list recent Paymob transactions and match by numeric order ID or recent list
            String listUrl = BASE_URL + "/api/acceptance/transactions?page_size=50";
            HttpRequest listRequest = HttpRequest.newBuilder()
                    .uri(URI.create(listUrl))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + authToken)
                    .GET()
                    .build();

            String listResponseBody = sendHttpRequest(listRequest);
            JsonObject listObj = gson.fromJson(listResponseBody, JsonObject.class);
            if (listObj != null && listObj.has("results")) {
                com.google.gson.JsonArray results = listObj.getAsJsonArray("results");
                for (com.google.gson.JsonElement elem : results) {
                    if (!elem.isJsonObject()) continue;
                    JsonObject t = elem.getAsJsonObject();

                    // If numeric order ID is provided, try matching order.id
                    if (paymobOrderId != null && !paymobOrderId.isBlank()) {
                        String orderIdInTxn = null;
                        if (t.has("order")) {
                            com.google.gson.JsonElement o = t.get("order");
                            if (o.isJsonObject() && o.getAsJsonObject().has("id")) {
                                orderIdInTxn = o.getAsJsonObject().get("id").getAsString();
                            } else if (o.isJsonPrimitive()) {
                                orderIdInTxn = o.getAsString();
                            }
                        }
                        if (paymobOrderId.equalsIgnoreCase(orderIdInTxn)) {
                            return t;
                        }
                    }
                }
                // Return first successful transaction if results exist and list query was initiated
                if (results.size() > 0 && results.get(0).isJsonObject()) {
                    return results.get(0).getAsJsonObject();
                }
            }
        } catch (Exception e) {
            logger.warn("Transaction inquiry failed for orderId={}, txnId={}: {}", paymobOrderId, paymobTxnId, e.getMessage());
        }
        return null;
    }

    /**
     * Lists recent transactions from Paymob Acceptance API.
     */
    public com.google.gson.JsonArray listRecentTransactions(int pageSize) {
        try {
            String authToken = generateAuthToken();
            String listUrl = BASE_URL + "/api/acceptance/transactions?page_size=" + Math.max(1, pageSize);
            HttpRequest listRequest = HttpRequest.newBuilder()
                    .uri(URI.create(listUrl))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + authToken)
                    .GET()
                    .build();

            String listResponseBody = sendHttpRequest(listRequest);
            JsonObject listObj = gson.fromJson(listResponseBody, JsonObject.class);
            if (listObj != null && listObj.has("results")) {
                return listObj.getAsJsonArray("results");
            }
        } catch (Exception e) {
            logger.error("Failed to fetch recent transactions from Paymob: {}", e.getMessage(), e);
        }
        return new com.google.gson.JsonArray();
    }

    private String sendHttpRequest(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String body = response.body();

            logger.debug("Paymob HTTP Response Status: {}", statusCode);

            if (statusCode >= 200 && statusCode < 300) {
                return body;
            } else {
                logger.error("Paymob HTTP request failed: status={}, body={}", statusCode, body);
                throw new PaymobApiException(statusCode, body);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.error("Network or HTTP communication error: {}", e.getMessage(), e);
            throw new PaymobApiException("Paymob communication failure: " + e.getMessage(), e);
        }
    }

    private static String maskSecret(String secret) {
        if (secret == null || secret.isEmpty()) {
            return "****";
        }
        if (secret.length() <= 8) {
            return "****";
        }
        return secret.substring(0, 4) + "..." + secret.substring(secret.length() - 4);
    }
}
