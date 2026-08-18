# 08. Complete REST API Endpoint Reference

## Overview
NexusIVR exposes REST API endpoints across two backend services:
- **`IVR-AI-engine`** (Default Base URL: `http://localhost:8081`)
- **`IVR-payment-service`** (Default Base URL: `http://localhost:8082`)

---

## 1. Authentication & System Health Endpoints

| Service | Method | Path | Auth Required | Request Body / Query Params | Response Shape | Frontend Caller |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `AI Engine` | `POST` | `/api/auth/login` | Public | `{ "email": "...", "password": "..." }` | `{ "token": "...", "user": { ... } }` | `LoginPage.tsx` |
| `AI Engine` | `GET` | `/health` | Public | None | `{ "status": "UP", "service": "IVR-AI-engine" }` | Docker / Healthcheck |
| `AI Engine` | `GET` | `/api/system-health` | Super Admin | None | `{ "database": "UP", "asteriskAmi": "UP", "aiProvider": "OPENROUTER", "memory": { ... } }` | `SystemHealth.tsx` |
| `Payment` | `GET` | `/health` | Public | None | `{ "status": "UP", "service": "IVR-payment-service" }` | Docker / Healthcheck |

---

## 2. Generative AI & Flow Builder Endpoints (`IVR-AI-engine`)

| Method | Path | Auth Required | Parameters / Body | Response Shape | Frontend Caller |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `POST` | `/ai/agent` | Tenant Admin | `{ "prompt": "...", "domain": "Banking" }` | `{ "flowModel": { "nodes": [...], "edges": [...] } }` | `IVRBuilder.tsx` |
| `POST` | `/ai/agent?action=refine_prompt` | Tenant Admin | `{ "prompt": "..." }` | `{ "refinedPrompt": "..." }` | `IVRBuilder.tsx` |
| `POST` | `/ai/agent?action=validate` | Tenant Admin | `{ "flowModel": { ... } }` | `{ "valid": true, "issues": [...] }` | `IVRBuilder.tsx` |
| `POST` | `/ai/agent?action=improve` | Tenant Admin | `{ "flowModel": { ... }, "instruction": "..." }` | `{ "patchedFlowModel": { ... } }` | `IVRBuilder.tsx` |
| `DELETE` | `/ai/agent` | Tenant Admin | `?sessionId=...` | `{ "cancelled": true }` | `IVRBuilder.tsx` |
| `POST` | `/ai/chat` | Tenant Admin | `{ "message": "...", "sessionId": "..." }` | `{ "reply": "...", "ragContext": [...] }` | `AIAssistant.tsx` |
| `GET` | `/ai/chat/history` | Tenant Admin | `?sessionId=...` | `{ "messages": [...] }` | `AIAssistant.tsx` |
| `POST` | `/ai/summarize` | Tenant Admin | `{ "conversationId": "..." }` | `{ "summary": "..." }` | `AIAssistant.tsx` |
| `POST` | `/ai/function-call` | Tenant Admin | `{ "toolName": "...", "args": { ... } }` | `{ "result": { ... } }` | `IVRBuilder.tsx` |

---

## 3. Telephony & Resource Management Endpoints (`IVR-AI-engine`)

| Method | Path | Auth Required | Parameters / Body | Response Shape | Frontend Caller |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/telephony/phone-numbers` | Tenant Admin | None | `[ { "id": "...", "phoneNumber": "+1...", "status": "ACTIVE" } ]` | `PhoneNumbers.tsx` |
| `POST` | `/api/telephony/phone-numbers` | Tenant Admin | `{ "phoneNumber": "+1...", "provider": "Twilio" }` | `{ "id": "...", "status": "UNASSIGNED" }` | `PhoneNumbers.tsx` |
| `PUT` | `/api/telephony/phone-numbers` | Tenant Admin | `{ "id": "...", "assignedFlowId": "..." }` | `{ "success": true }` | `PhoneNumbers.tsx` |
| `DELETE` | `/api/telephony/phone-numbers` | Tenant Admin | `?id=...` | `{ "success": true }` | `PhoneNumbers.tsx` |
| `GET` | `/api/telephony/queues` | Tenant Admin | None | `[ { "id": "...", "name": "Support L1", "strategy": "round_robin" } ]` | `QueueManagement.tsx` |
| `POST` | `/api/telephony/queues` | Tenant Admin | `{ "name": "Sales Queue", "strategy": "least_recent" }` | `{ "id": "...", "status": "active" }` | `QueueManagement.tsx` |
| `DELETE` | `/api/telephony/queues` | Tenant Admin | `?id=...` | `{ "success": true }` | `QueueManagement.tsx` |
| `GET` | `/api/telephony/sip-extensions` | Tenant Admin | None | `[ { "id": "...", "extensionNumber": "1001", "displayName": "Alex" } ]` | `SIPExtensions.tsx` |
| `POST` | `/api/telephony/sip-extensions` | Tenant Admin | `{ "extensionNumber": "1002", "displayName": "Sarah", "sipPassword": "..." }` | `{ "id": "...", "extensionNumber": "1002" }` | `SIPExtensions.tsx` |
| `GET` | `/api/voice-prompts` | Tenant Admin | None | `[ { "id": "...", "name": "Welcome", "filePath": "/prompts/welcome.wav" } ]` | `VoicePrompts.tsx` |
| `POST` | `/api/voice-prompts/generate` | Tenant Admin | `{ "text": "Welcome to NexusIVR", "language": "en-US" }` | `{ "id": "...", "filePath": "..." }` | `VoicePrompts.tsx` |
| `GET` | `/api/voice-prompts/stream` | Tenant Admin | `?id=...` | Audio/WAV Binary Stream | `VoicePrompts.tsx` |

---

## 4. Analytics, Dashboard & Notification Endpoints (`IVR-AI-engine`)

| Method | Path | Auth Required | Parameters / Body | Response Shape | Frontend Caller |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/dashboard` | Tenant Admin | None | `{ "totalCalls": 125, "successRate": 98.4, "activeFlows": 4, "recentCalls": [...] }` | `TenantAdminDashboard.tsx` |
| `GET` | `/api/cdr` | Tenant Admin | None | `{ "summary": { "total": 45, "answered": 40 }, "records": [...] }` | `CallAnalytics.tsx` |
| `GET` | `/api/notifications` | Tenant/Super | None | `[ { "id": "...", "message": "...", "isRead": false } ]` | `NotificationBell.tsx` |
| `POST` | `/api/notifications` | Tenant/Super | `{ "action": "mark_read", "notificationId": "..." }` | `{ "success": true }` | `NotificationBell.tsx` |
| `GET` | `/api/audit-logs` | Super Admin | `?page=1&limit=20` | `{ "logs": [...], "total": 150 }` | `AuditLogs.tsx` |

---

## 5. Super Admin Platform Administration Endpoints (`IVR-AI-engine`)

| Method | Path | Auth Required | Parameters / Body | Response Shape | Frontend Caller |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/super-admin/dashboard` | Super Admin | None | `{ "totalTenants": 12, "activeUsers": 45, "totalRevenue": 150000 }` | `SuperAdminDashboard.tsx` |
| `GET` | `/api/super-admin/companies` | Super Admin | None | `[ { "id": "...", "displayName": "Company A", "status": "ACTIVE" } ]` | `SuperAdminCompanies.tsx` |
| `POST` | `/api/super-admin/companies/status` | Super Admin | `{ "tenantId": "...", "status": "SUSPENDED" }` | `{ "success": true }` | `SuperAdminCompanies.tsx` |
| `POST` | `/api/super-admin/companies/override-plan` | Super Admin | `{ "tenantId": "...", "planId": "..." }` | `{ "success": true }` | `SuperAdminCompanies.tsx` |
| `GET` | `/api/super-admin/users` | Super Admin | None | `[ { "id": "...", "email": "...", "status": "ACTIVE" } ]` | `SuperAdminUsers.tsx` |
| `POST` | `/api/super-admin/users/status` | Super Admin | `{ "userId": "...", "status": "SUSPENDED" }` | `{ "success": true }` | `SuperAdminUsers.tsx` |
| `GET` | `/api/super-admin/reports` | Super Admin | None | `{ "tenantGrowth": [...], "revenueByPlan": [...] }` | `Reports.tsx` |
| `GET` | `/api/super-admin/reports/export-csv` | Super Admin | `?type=tenants` | CSV File Download Stream | `Reports.tsx` |
| `GET` | `/ai/providers` | Super Admin | None | `{ "activeProvider": "OPENROUTER", "providers": [...] }` | `SuperAdminSettings.tsx` |
| `POST` | `/ai/providers` | Super Admin | `{ "provider": "GEMINI" }` | `{ "success": true, "activeProvider": "GEMINI" }` | `SuperAdminSettings.tsx` |

---

## 6. Paymob & Subscription Endpoints (`IVR-payment-service`)

| Method | Path | Auth Required | Parameters / Body | Response Shape | Frontend Caller |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/payment/plans` | Tenant Admin | None | `[ { "id": "...", "name": "Business", "pricePiasters": 150000 } ]` | `TenantBilling.tsx` |
| `GET` | `/api/payment/billing-status` | Tenant Admin | None | `{ "planName": "Business", "status": "ACTIVE", "expiresAt": "2026-09-17..." }` | `TenantBilling.tsx` |
| `POST` | `/api/payment/initiate` | Tenant Admin | `{ "planId": "...", "billingData": { ... } }` | `{ "iframeUrl": "https://accept.paymob.com/...", "transactionId": "..." }` | `TenantBilling.tsx` |
| `POST` | `/api/payment/callback` | Paymob Webhook | Paymob Webhook JSON | `{ "received": true }` | Paymob Gateway |
| `GET` | `/api/payment/callback` | Browser | Query String (`?hmac=...&success=true`) | HTTP 302 Redirect to `/payment-callback` | Client Browser |
| `GET` | `/api/payment/verify` | Tenant Admin | `?id=...` | `{ "status": "SUCCESS", "paymobTxnId": "..." }` | `PaymentCallback.tsx` |
