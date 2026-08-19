# 05. IVR-Payment-Service Specification

## Service Overview & Payment Architecture
- **Runtime Environment**: Java 21 / Jakarta Servlet 6.0 / Embedded Apache Tomcat 10 (Port `8082`)
- **Main Package**: `com.nexusivr.payment`
- **Core Responsibilities**:
  - Commercial subscription plan catalog management (`Starter`, `Business`, `Enterprise`).
  - Paymob payment gateway integration (Authentication, Order Registration, Payment Key Generation, Payment Iframe URL).
  - Secure webhook & callback handling with SHA-512 HMAC signature verification.
  - Automated tenant subscription provisioning upon successful transaction completion.
  - In-app notification creation upon payment events.
  - Background cron scheduler (`SubscriptionScheduler`) enforcing subscription expiration.

---

## Detailed Class Reference

### 1. Configuration & Startup Layer

#### `AppServletContextListener`
- **Responsibility**: Servlet container lifecycle listener initializing database pool, validating Paymob configuration credentials, and starting `SubscriptionScheduler`.
- **Public Methods**:
  - `contextInitialized(ServletContextEvent)`: Validates `PAYMOB_API_KEY` and `PAYMOB_HMAC_SECRET`, initializes `DatabaseManager`, schedules daily subscription expiration cron.
  - `contextDestroyed(ServletContextEvent)`: Shuts down database connection pools and executor services cleanly.

#### `PaymobConfig`
- **Responsibility**: Configuration bean holding Paymob API credentials and integration IDs read from `.env` or system environment variables.
- **Key Parameters**:
  - `PAYMOB_API_KEY`: API authentication key.
  - `PAYMOB_SECRET_KEY` & `PAYMOB_PUBLIC_KEY`: Paymob secret/public keys.
  - `PAYMOB_HMAC_SECRET`: Secret key used for SHA-512 HMAC callback signature calculation (`8FA7D7A2A4FCE7C0FF9BABF4FAB65CE2`).
  - `PAYMOB_INTEGRATION_ID_CARD`: Card integration ID (`5834828`).
  - `PAYMOB_IFRAME_ID`: Hosted payment iframe ID (`1067447`).

---

### 2. Controller & Servlet Layer

#### `HealthServlet` (`/health`)
- **Responsibility**: Liveness probe returning HTTP 200 JSON status.

#### `PaymentServlet` (`/api/payment/*`)
- **Responsibility**: Handles subscription checkout requests, plan lookups, billing status, and Paymob payment callbacks.
- **Supported Endpoints & Workflows**:
  - **`GET /api/payment/plans`**: Calls `SubscriptionPlanDao.findAll()` to return available SaaS plans.
  - **`GET /api/payment/billing-status`**: Decodes JWT from request, queries current tenant subscription status, active plan name, and `subscription_expires_at` date.
  - **`POST /api/payment/initiate`**: Decodes JWT, parses `planId`, calls `PaymentService.initiateSubscriptionPayment()`. Creates `PENDING` record in `transactions` table and returns Paymob checkout iframe URL.
  - **`POST /api/payment/callback` & `GET /api/payment/callback`**: Handles Paymob payment notification. Invokes `HmacVerifier.verify()`. On valid HMAC signature and `success == true`:
    1. Updates transaction status to `SUCCESS` in `transactions` table.
    2. Updates `tenants` table: sets `subscription_plan_id = planId`, `subscription_status = 'ACTIVE'`, `subscription_expires_at = NOW() + 30 days`.
    3. Inserts notification record in `notifications` table.
    4. Redirects client browser to `/payment-callback?status=success`.
  - **`GET /api/payment/verify`**: Queries Paymob API directly to check transaction status if client missed callback.

---

### 3. Core Logic & Utility Layer

#### `PaymentService`
- **Responsibility**: High-level payment orchestrator interfacing with `PaymobHttpClient`, `SubscriptionPlanDao`, and `TransactionDao`.
- **Public Methods**:
  - `initiateSubscriptionPayment(UUID tenantId, UUID planId, BillingData billingData)`: Executes 3-step Paymob handshake:
    1. Step 1: Obtains Paymob Auth Token (`POST /api/auth/tokens`).
    2. Step 2: Registers Order (`POST /api/ecommerce/orders`).
    3. Step 3: Obtains Payment Key (`POST /api/acceptance/payment_keys`).
    4. Constructs hosted iframe URL (`https://accept.paymob.com/api/acceptance/iframes/{iframe_id}?payment_token={token}`).
    5. Saves `PENDING` transaction to database.

#### `HmacVerifier`
- **Responsibility**: SHA-512 HMAC cryptographic signature validator preventing callback tampering.
- **HMAC Calculation Sequence**: Concatenates callback JSON fields in exact sequence (`amount_cents`, `created_at`, `currency`, `error_occured`, `has_parent_transaction`, `id`, `integration_id`, `is_3d_secure`, `is_auth`, `is_capture`, `is_standalone_payment`, `is_voided`, `order.id`, `owner`, `pending`, `source_data.pan`, `source_data.sub_type`, `source_data.type`, `success`), hashes using `PAYMOB_HMAC_SECRET` via HmacSHA512, and compares against `hmac` parameter.

#### `SubscriptionScheduler`
- **Responsibility**: Background task executing every 24 hours. Checks for tenants where `subscription_expires_at < NOW()` and updates `subscription_status = 'INACTIVE'`.

#### `SubscriptionPlanDao` & `TransactionDao`
- **Responsibility**: Data Access Objects executing SQL queries against `subscription_plans`, `transactions`, and `tenants` tables.
