# 10. Known Limitations, Dummy Data & Inconsistencies

## Feature Implementation Status Matrix (Real vs. Mocked)

| Platform Feature | Backend Database Schema | DAO & Service Layer | REST API Endpoint | Frontend UI Screen | Actual Status (Real vs. Mocked) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Authentication & Users** | `users`, `tenants` (Real) | `UserDao`, `TenantDao` (Real) | `/api/auth/login` (Real) | `LoginPage.tsx` (Real) | **Fully Real**: JWT auth, SHA-256 password hashing, tenant scoping. |
| **7-Pass Generative AI Builder**| In-memory / Drafts (Real) | `DomainFlowGenerator` (Real) | `/ai/agent` (Real) | `IVRBuilder.tsx` (Real) | **Fully Real**: OpenRouter/Gemini/Groq/Ollama LLM generation, VXML compile. |
| **Flow Publishing & Asterisk** | `/app/scenarios/*.vxml` (Real) | `FlowPublishService` (Real) | `/api/flows/publish` (Real) | `IVRBuilder.tsx` (Real) | **Fully Real**: Exports `.vxml`, runs `add_extension.sh`, reloads Asterisk dialplan. |
| **FastAGI Scenario Execution** | `call_logs`, `call_events` (Real) | `VxmlAgiHandler` (Real) | Port `4573` FastAGI (Real) | Telephony Channel (Real) | **Fully Real**: FastAGI listener, JVoiceXML engine, Asterisk channel control. |
| **Paymob Payments & Subscriptions**| `subscription_plans`, `transactions` (Real)| `PaymentService` (Real) | `/api/payment/*` (Real) | `TenantBilling.tsx` (Real) | **Fully Real**: Paymob REST API, Order registration, iframe URL, HMAC verification. |
| **SIP Extensions Management** | `sip_extensions` (Real) | `SipExtensionDao` (Real) | `/api/telephony/sip-extensions` (Real) | `SIPExtensions.tsx` (Real) | **Fully Real**: Real DB storage, PJSIP configuration generation, real UI integration. |
| **Call Queue Management** | `queues`, `queue_members`, `agent_states` (Real)| `QueueDao`, `AgentStateDao` (Real) | `/api/telephony/queues` (Real) | `QueueManagement.tsx` (Real) | **Fully Real**: Real DB storage, ACD strategy definition, agent state tracking. |
| **Voice Prompts Library & TTS** | `voice_prompts` (Real) | `VoicePromptDao`, `TtsEngine` (Real)| `/api/voice-prompts/*` (Real) | `VoicePrompts.tsx` (Real) | **Fully Real**: Saves `.wav` into `/var/lib/asterisk/sounds`, streams WAV audio back. |
| **In-App Notifications** | `notifications` (Real) | `NotificationDao` (Real) | `/api/notifications` (Real) | `NotificationBell.tsx` (Real) | **Fully Real**: Real DB persistence, 30s UI polling, mark-as-read updates. |
| **Security Audit Logs** | `audit_logs` (Real) | `AuditLogDao` (Real) | `/api/audit-logs` (Real) | `AuditLogs.tsx` (Real) | **Fully Real**: Real DB logging on login, publish, company creation, plan overrides. |
| **System Health Diagnostics** | System / Socket (Real) | `SystemHealthService` (Real) | `/api/system-health` (Real) | `SystemHealth.tsx` (Real) | **Fully Real**: Real JDBC check, Asterisk AMI socket probe, JVM memory inspection. |
| **Phone Numbers (DIDs)** | `phone_numbers` (Real) | `PhoneNumberDao` (Real) | `/api/telephony/phone-numbers` (Real) | `PhoneNumbers.tsx` (Partial Mock) | **Partially Mocked**: Backend DB schema, DAO, and REST servlets are fully implemented, but `PhoneNumbers.tsx` frontend screen still uses static dummy state in UI controls. |

---

## Code Inconsistencies & Technical Debts

1. **Duplicate VoiceXML Validator Implementations**:
   - `gov.iti.telecom.VxmlValidator` (located in `IVR-engine`)
   - `com.nexusivr.ai.service.VxmlValidator` (located in `IVR-AI-engine`)
   - *Impact*: Minor logic duplication. Both validate XML structure, but `IVR-engine` uses Java DOM while `IVR-AI-engine` uses XML SAX parser.

2. **Password Utility Hashing vs Seed Data Plaintext**:
   - `PasswordUtil` in `IVR-AI-engine` hashes passwords with SHA-256.
   - However, seed migration `009_users_and_tenants.sql` inserts `admin` and `user` as raw strings. The `UserDao` handles fallback plain text comparison for legacy seeded accounts, but new user registrations are hashed.

3. **OpenRouter Base URL Proxy Mapping**:
   - In `.env`, `OPENROUTER_BASE_URL` is configured to point to an ITI proxy endpoint (`http://apiaccess.iti.net.eg/api/v1/student/chat`).
   - `OpenAiCompatibleClient.java` contains fallback default to standard `https://openrouter.ai/api/v1`.

---

## Explicit "Needs Clarification" Items

1. **Paymob E-Wallet & MOTO Integration IDs (`Needs Clarification`)**:
   - In `IVR-payment-service/.env`, `PAYMOB_INTEGRATION_ID_CARD` (`5834828`) is real and verified.
   - However, `PAYMOB_INTEGRATION_ID_WALLET` (`5834850`) and `PAYMOB_MOTO_INTEGRATION_ID` (`5834828`) are set to placeholder/test IDs. Production Paymob dashboard credentials for mobile wallet payments require explicit verification.

2. **Carrier DID API Purchasing (`Needs Clarification`)**:
   - The `phone_numbers` schema supports Twilio and Vonage carrier fields.
   - Currently, phone number creation executes database inserts. Live API purchasing (e.g. calling Twilio REST API `IncomingPhoneNumbers.json` to buy a real DID) is not yet wired to carrier APIs and requires clarification if live carrier provisioning is needed.
