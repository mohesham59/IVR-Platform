# 06. IVR-Webapp Frontend Specification

## Frontend Architecture & Technology Stack
- **Framework**: React 19 / TypeScript 5 / Vite
- **Styling**: Vanilla CSS (`index.css` design system with CSS custom properties, dark mode, glassmorphism, micro-animations) & Tailwind CSS utilities.
- **Main Package**: `IVR-webapp/src`
- **State & Storage**: React State/Hooks, Browser `localStorage` (`nexusivr_token`, `nexusivr_user`).
- **HTTP Client**: Axios with automatic JWT Bearer token injection (`src/api/aiApi.ts`).

---

## Screen & Page Specifications (`src/screens/`)

### 1. Common / Shared Screens

#### `LoginPage.tsx` (`/login`)
- **Purpose**: Platform authentication screen for Super Admins and Tenant Users.
- **API Calls**: `POST /api/auth/login` (or `/ai/auth/login`)
- **State Managed**: `email`, `password`, `errorMessage`, `isLoading`.
- **Workflow**: On submission, receives JWT token and user object. Stores `nexusivr_token` and `nexusivr_user` in `localStorage`. If `user.is_superadmin === true`, redirects to `/superadmin`; otherwise redirects to `/dashboard`.

#### `PaymentCallback.tsx` (`/payment-callback`)
- **Purpose**: Paymob payment redirect landing screen after customer completes checkout.
- **API Calls**: `GET /api/payment/verify?id={transactionId}`
- **State Managed**: `status` (`success`, `failed`, `pending`), `transactionDetails`.
- **Workflow**: Reads URL query parameters (`success`, `id`). Displays confirmation badge and updates tenant subscription state.

---

### 2. Tenant Admin Screens (`TenantLayout.tsx`)

#### `TenantAdminDashboard.tsx` (`/dashboard`)
- **Purpose**: Primary operational dashboard for tenant administrators.
- **API Calls**: `GET /api/dashboard`
- **State Managed**: `stats` (Total Calls, Success Rate, Active Flows, Active Numbers, Queues Count), `recentCalls` list.
- **Components**: Quick action cards, recent call history table, call volume charts.

#### `IVRBuilder.tsx` (`/ivr-builder`)
- **Purpose**: Visual Drag-and-Drop IVR Flow Canvas & 7-Pass AI Flow Generator.
- **API Calls**: `POST /ai/agent` (Generate/Validate/Improve), `POST /api/flows/publish`
- **State Managed**: `flowModel` (Nodes, Edges), `selectedNodeId`, `isGenerating`, `generationStep` (1-7), `vxmlPreview`.
- **Components**: `FlowCanvas`, `NodeLibrary`, `PropertiesPanel`, `AiAssistantPanel`, `GenerationStepper`.

#### `AIAssistant.tsx` (`/ai-assistant`)
- **Purpose**: Conversational AI assistant for testing IVR logic and querying knowledge base docs.
- **API Calls**: `POST /ai/chat`, `GET /ai/chat/history`
- **State Managed**: `messages` array, `sessionId`, `inputPrompt`, `isThinking`.

#### `CallAnalytics.tsx` (`/call-analytics`)
- **Purpose**: Detailed telephony Call Detail Records (CDR) and menu distribution metrics.
- **API Calls**: `GET /api/cdr`, `GET /api/telephony/analytics`
- **State Managed**: `cdrRecords`, `callMetrics`, `dateRangeFilter`, `statusFilter`.

#### `QueueManagement.tsx` (`/queues`)
- **Purpose**: Call center queue configuration and real-time agent presence monitoring.
- **API Calls**: `GET /api/telephony/queues`, `POST /api/telephony/queues`, `DELETE /api/telephony/queues`
- **State Managed**: `queues` list, `agents` list, `newQueueModalOpen`, `selectedQueue`.

#### `SIPExtensions.tsx` (`/sip-extensions`)
- **Purpose**: Management of PJSIP extensions for desk phones and WebRTC softphones.
- **API Calls**: `GET /api/telephony/sip-extensions`, `POST /api/telephony/sip-extensions`
- **State Managed**: `extensions` list, `addExtensionModalOpen`.

#### `VoicePrompts.tsx` (`/voice-prompts`)
- **Purpose**: System audio prompt library management and text-to-speech audio synthesis.
- **API Calls**: `GET /api/voice-prompts`, `POST /api/voice-prompts/generate`, `GET /api/voice-prompts/stream`
- **State Managed**: `promptsList`, `ttsTextInput`, `selectedLanguage`, `isSynthesizing`.

#### `TenantBilling.tsx` (`/billing`)
- **Purpose**: Tenant subscription management, current plan tier, and Paymob checkout initiation.
- **API Calls**: `GET /api/payment/plans`, `GET /api/payment/billing-status`, `POST /api/payment/initiate`
- **State Managed**: `plans` list, `currentSubscription`, `isRedirecting`.

#### `Settings.tsx` (`/settings`)
- **Purpose**: Tenant company profile settings, API keys, and notification preferences.

---

### 3. Super Admin Screens (`SuperAdminLayout.tsx`)

#### `SuperAdminDashboard.tsx` (`/superadmin`)
- **Purpose**: Platform-wide health overview, active tenant counts, subscription revenue, platform call statistics.
- **API Calls**: `GET /api/super-admin/dashboard`

#### `SuperAdminCompanies.tsx` (`/superadmin/companies`)
- **Purpose**: Complete listing of all SaaS tenant accounts with status toggling and subscription plan overriding.
- **API Calls**: `GET /api/super-admin/companies`, `POST /api/super-admin/companies/override-plan`, `POST /api/super-admin/companies/status`
- **Workflow**: Includes a modal dialog requiring explicit Super Admin confirmation before overriding a tenant's plan. Writes an audit log record (`PLAN_OVERRIDE`).

#### `SuperAdminUsers.tsx` (`/superadmin/users`)
- **Purpose**: Management of all platform users across all tenants.
- **API Calls**: `GET /api/super-admin/users`, `POST /api/super-admin/users/status`

#### `SuperAdminSubscriptions.tsx` (`/superadmin/subscriptions`)
- **Purpose**: Master management of SaaS pricing tiers and Paymob integration ID mappings.

#### `Reports.tsx` (`/superadmin/reports`)
- **Purpose**: Comprehensive platform analytics reporting with CSV export capabilities.
- **API Calls**: `GET /api/super-admin/reports`, `GET /api/super-admin/reports/export-csv`

#### `AuditLogs.tsx` (`/superadmin/audit-logs`)
- **Purpose**: System-wide security and operational audit trail viewer.
- **API Calls**: `GET /api/audit-logs`

#### `SystemHealth.tsx` (`/superadmin/system-health`)
- **Purpose**: Real-time diagnostic monitor showing database, Asterisk AMI, disk space, and JVM status.
- **API Calls**: `GET /api/system-health`

#### `SuperAdminSettings.tsx` (`/superadmin/settings`)
- **Purpose**: AI provider configuration (switching primary provider between Gemini, Groq, OpenRouter, and Ollama) and circuit breaker controls.
- **API Calls**: `GET /ai/providers`, `POST /ai/providers`

---

## Visual IVR Builder Engine (`src/ivr/`)

```
                  +--------------------------------------------------+
                  |                 FlowCanvas.tsx                   |
                  |  - Visual Drag-and-Drop Canvas                   |
                  |  - Renders Nodes, Edges, Connections             |
                  +------------------------+-------------------------+
                                           |
                  +------------------------+-------------------------+
                  |                graphEngine.ts                    |
                  |  - Graph State & Topology Manager                |
                  |  - Node Addition / Deletion                      |
                  |  - Edge Connection & Validation                  |
                  +------------------------+-------------------------+
                                           |
                 +-------------------------+-------------------------+
                 |                                                   |
                 v                                                   v
+----------------------------------+               +----------------------------------+
|          flowParser.ts           |               |         vxmlExporter.ts          |
|  - Deserializes JSON / VXML      |               |  - Compiles visual FlowModel     |
|    into visual Node Graph        |               |    into VoiceXML 2.1 Document     |
+----------------------------------+               +----------------------------------+
```
