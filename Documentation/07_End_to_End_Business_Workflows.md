# 07. End-to-End Business Workflows

## 1. Authentication & Authorization Flow (JWT & RBAC)

```
[User / Browser] ------------(POST /api/auth/login)------------> [IVR-AI-engine]
                                                                        |
                                                                        v
                                                               [UserDao.findByEmail]
                                                                        |
                                                                        v
                                                           [PasswordUtil.verifyPassword]
                                                                        |
                                                                        v
[Browser localStorage] <--(JWT Token + User Object)-- [JwtUtil.generateToken(user)]
```

### Technical Sequence:
1. **User Submission**: User submits email and password at `/login`.
2. **Credential Lookup**: `BaseAiServlet` routes to authentication handler. `UserDao.findByEmail(email)` retrieves user record from `users` table.
3. **Password Verification**: `PasswordUtil` checks password hash (SHA-256 with salt or fallback comparison).
4. **JWT Token Generation**: `JwtUtil.generateToken()` constructs JWT payload containing:
   - `sub`: User ID (UUID)
   - `email`: User email
   - `tenantId`: Active tenant ID (or `null` if SuperAdmin)
   - `isSuperadmin`: Boolean flag (`true`/`false`)
   - `exp`: Expiration timestamp (24 hours)
5. **Client Session Storage**: Frontend receives response and writes:
   - `localStorage.setItem('nexusivr_token', token)`
   - `localStorage.setItem('nexusivr_user', JSON.stringify(user))`
6. **Request Interception**: On subsequent HTTP calls, `src/api/aiApi.ts` Axios interceptor attaches header:
   ```http
   Authorization: Bearer <token>
   ```
7. **Backend Scope Enforcement**: `BaseAiServlet.extractTenantId(req)` extracts `tenantId`. Every DAO SQL query appends `WHERE tenant_id = ?` using this extracted UUID.

---

## 2. Complete IVR Flow Lifecycle

```
[Natural Language Prompt]
           |
           v
[7-Pass AI Generator (DomainFlowGenerator)]
           |
           v
[FlowModel JSON Graph (Draft Saved in /IVR-engine/draft/)]
           |
           v
[FlowModelValidator.validate()]
           |
           v
[ModelToVxmlExporter (Compiles to VoiceXML 2.1 XML)]
           |
           v
[Saves VXML to /app/IVR-engine/scenarios/{tenant_id}_{flow}.vxml]
           |
           v
[Invokes ./IVR-engine/add_extension.sh (Injects into extensions.conf & reloads AMI)]
           |
           v
[Asterisk Channel calls AGI(agi://127.0.0.1:4573/default)]
           |
           v
[FastAGI Server (VxmlAgiHandler) executes VoiceXML Dialogue on Channel]
```

### Technical Steps:
1. **Draft Generation**: User enters prompt (e.g. "Create a bilingual hotel booking IVR").
2. **7-Pass Execution**: `UnifiedAiEngine` triggers `DomainFlowGenerator` passes 1-7, returning `FlowModel` JSON.
3. **Draft Storage**: `FlowDao.saveDraft()` writes JSON file to `IVR-engine/draft/`.
4. **Publish Trigger**: User clicks "Publish IVR".
5. **Validation**: `FlowModelValidator` verifies graph topology, prompt presence, and choice targets.
6. **VXML Compilation**: `ModelToVxmlExporter.exportToVxml(flowModel)` transforms visual JSON into VoiceXML 2.1 document string.
7. **Scenario Export**: File saved to `/app/IVR-engine/scenarios/{tenant_id}_{flow_name}.vxml`.
8. **Dialplan Auto-Provisioning**: `FlowPublishService` executes `add_extension.sh 1001 {tenant_id}_{flow_name}`:
   - Appends dialplan stanza to `/etc/asterisk/extensions.conf`.
   - Sends AMI socket command `asterisk -rx 'dialplan reload'`.
9. **Telephony Execution**: External call to extension `1001` triggers Asterisk AGI socket request to `IVR-engine` on port `4573`. `VxmlAgiHandler` executes VXML scenario on channel.

---

## 3. Paymob Payment & Subscription Activation Workflow

```
[Tenant Admin] ------------(POST /api/payment/initiate)------------> [IVR-payment-service]
                                                                              |
                                                                              v
                                                                   [PaymobHttpClient]
                                                                 1. Get Token
                                                                 2. Register Order
                                                                 3. Get Payment Key
                                                                              |
                                                                              v
[Browser Redirect] <------------(Iframe Checkout URL)-------------------------+
        |
        v
[Paymob Checkout Page (Card Payment)]
        |
        v
[Paymob Webhook] ------------(POST /api/payment/callback)------------> [IVR-payment-service]
                                                                              |
                                                                              v
                                                                     [HmacVerifier.verify]
                                                                   (SHA-512 Hash Verification)
                                                                              |
                                                                              v
                                                                 [Update DB Transactions: SUCCESS]
                                                                 [Update DB Tenants: ACTIVE]
                                                                 [Insert Notification Record]
```

### Technical Sequence:
1. **Initiation**: Tenant Admin selects plan (`Business` - 150,000 Piasters) at `/billing`.
2. **Paymob Handshake**: `PaymentService.initiateSubscriptionPayment()`:
   - Obtains Paymob Token (`POST /api/auth/tokens`).
   - Registers Order (`POST /api/ecommerce/orders`).
   - Obtains Payment Key (`POST /api/acceptance/payment_keys`).
   - Writes `PENDING` record to `transactions` table.
3. **User Checkout**: User is redirected to Paymob iframe URL and inputs credit card details.
4. **Callback Processing**: Paymob posts webhook JSON payload to `/api/payment/callback`.
5. **HMAC Verification**: `HmacVerifier.verify(payload)` computes SHA-512 signature using `PAYMOB_HMAC_SECRET`. If signature matches and `success == true`:
   - `TransactionDao.updateStatus(transactionId, 'SUCCESS')`.
   - `TenantDao.updateSubscription(tenantId, planId, 'ACTIVE', NOW() + 30 days)`.
   - `NotificationDao.insert(tenantId, 'Subscription upgraded to Business Plan')`.
   - Browser is redirected to `/payment-callback?status=success`.

---

## 4. Multi-Tenant Isolation Mechanism

### DAO Implementation Trace:
Multi-tenancy is enforced at the database level by tenant scoping on every single query.

#### Sample DAO Query Code (`SipExtensionDao.java`):
```java
public List<SipExtension> findByTenantId(UUID tenantId) throws SQLException {
    String sql = "SELECT id, tenant_id, extension_number, display_name, sip_password, tls_enabled, created_at, updated_at " +
                 "FROM sip_extensions WHERE tenant_id = ? ORDER BY extension_number ASC";
    List<SipExtension> list = new ArrayList<>();
    try (Connection conn = DatabaseManager.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setObject(1, tenantId);
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
    }
    return list;
}
```
- The `tenantId` is never trusted from client request parameters. It is decoded directly from the verified JWT Bearer token in `BaseAiServlet.extractTenantId(req)`.
- If a user attempts to access resources belonging to another tenant, the SQL query filters results by their own `tenant_id`, returning empty results or raising access errors.

---

## 5. Super Admin Plan Override Flow

```
[Super Admin] ---> [Click "Override Plan" on Tenant Company]
                        |
                        v
               [Confirmation Modal Dialog]
               ("Are you sure you want to override plan for Company X?")
                        |
                        v (Confirmed)
               [POST /api/super-admin/companies/override-plan]
                        |
                        v
               [TenantDao.updateSubscription(tenantId, planId, 'ACTIVE', expiresAt)]
               [AuditLogDao.insert('PLAN_OVERRIDE', tenantId, actorEmail, details)]
               [NotificationDao.insert(tenantId, 'Your subscription plan was updated by Super Admin')]
```

---

## 6. Notification System Workflow

1. **Trigger**: An event occurs (e.g. `PAYMENT_SUCCESS`, `PLAN_OVERRIDE`, `IVR_PUBLISHED`).
2. **Storage**: `NotificationDao.insertNotification(tenantId, userId, message, linkUrl, type)` inserts a row into `notifications` table (`is_read = false`).
3. **Retrieval & Polling**: `NotificationBell.tsx` polls `GET /api/notifications` every 30 seconds.
4. **Display**: Displays unread badge counter and dropdown list.
5. **Mark Read**: User clicks notification or "Mark all as read" button (`POST /api/notifications` with `action=mark_read`).

---

## 7. AI Provider Orchestration & Fallback Chain

```
                   +----------------------------+
                   |     UnifiedAiEngine        |
                   +--------------+-------------+
                                  |
                                  v
                   +----------------------------+
                   |      ProviderManager       |
                   +--------------+-------------+
                                  |
        +-------------------------+-------------------------+
        |                         |                         |
        v                         v                         v
[Primary: OpenRouter]    [CircuitBreaker]           [Fallback 1: Groq]
  (gpt-oss-20b)            (Check Failure Rate)      (llama-3.3-70b)
        |                         |                         |
        | (Fail/Timeout)          | (Open)                  | (Fail/Timeout)
        +-------------------------+                         v
                                                   [Fallback 2: Gemini]
                                                     (gemini-2.0-flash)
                                                            |
                                                            | (Fail/Timeout)
                                                            v
                                                   [Fallback 3: Ollama]
                                                     (granite4.1:8b local)
```

1. **Attempt Primary**: `ProviderManager` invokes configured primary provider (`AI_PROVIDER=openrouter`).
2. **Circuit Breaker Check**: `CircuitBreaker.allowRequest()` checks consecutive failures. If failure threshold exceeded, circuit transitions to `OPEN` state.
3. **Failover**: If primary throws exception (429 Rate Limit, 500 Server Error, Timeout), `ProviderManager` catches `ProviderException`, logs warning, and retries with next fallback provider in chain (`Groq` → `Gemini` → `Ollama`).
