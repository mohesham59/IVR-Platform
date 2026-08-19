# 01. System Architecture & Repository Structure

## Executive Summary & System Overview

**NexusIVR** is an enterprise multi-tenant AI-powered Interactive Voice Response (IVR) and Contact Center Software-as-a-Service (SaaS) platform. It provides a visual IVR flow builder, an automated 7-pass generative AI flow engine, a RAG (Retrieval-Augmented Generation) knowledge base engine, telephony analytics, Paymob-integrated subscription management, real-time telephony state controls, and integration with an Asterisk PBX cluster via FastAGI and AMI (Asterisk Manager Interface).

```
 +-----------------------------------------------------------------------------------+
 |                                   IVR-webapp                                      |
 |                           (React 19 + TypeScript + Vite)                          |
 +----------------------------------------+------------------------------------------+
                                          |
                        HTTP / REST APIs  |
          +-------------------------------+-------------------------------+
          |                                                               |
          v                                                               v
+-----------------------------------+                           +-----------------------------------+
|          IVR-AI-engine            |                           |        IVR-payment-service         |
|      (Port 8081 / Tomcat 10)      |                           |      (Port 8082 / Tomcat 10)      |
|  - Auth & Role Management (JWT)   |                           |  - Paymob Payment Integration     |
|  - 7-Pass Generative AI Engine    |                           |  - Subscription Plans & Billing   |
|  - Flow Drafts & Publishing       |                           |  - HMAC Callback Verification     |
|  - RAG / Vector Knowledge Base    |                           |  - Subscription Renewal Scheduler |
|  - System Health & Telephony State|                           +-----------------+-----------------+
|  - Audit Logs & Notifications     |                                             |
+-----------------+-----------------+                                             |
                  |                                                               |
                  | Shared DB / Scenarios / AMI                                   | PostgreSQL (NeonDB)
                  |                                                               |
                  +-------------------------------+-------------------------------+
                                                  |
                                                  v
+-----------------------------------+   +-----------------------------------------------------------+
|            IVR-engine             |   |                    PostgreSQL Database                    |
|   (FastAGI Server - Port 4573)    |   |                  (NeonDB + pgvector)                      |
|  - JVoiceXML Execution Platform   |   |  - Core AI Engine & Tenant Tables                         |
|  - VXML Scenario Loader           |   |  - Payment & Subscription Tables                          |
|  - Asterisk Spoken Input & TTS    |   |  - Call Logs, Extensions, Queues, Voice Prompts           |
|  - Call Event Analytics Logging   |   +-----------------------------------------------------------+
+-----------------+-----------------+
                  | FastAGI Protocol (Port 4573)
                  v
+---------------------------------------------------------------------------------------------------+
|                                         Asterisk PBX                                              |
|  - Audio Recordings & Sound Storage (/asterisk-sounds, /dev/shm)                                  |
|  - Call Detail Records (Master.csv in /asterisk-cdr)                                              |
|  - AMI Management Socket                                                                         |
+---------------------------------------------------------------------------------------------------+
```

---

## Inter-Service Communication Matrix

| Source Service | Target Service / Component | Communication Protocol | Port / Transport | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| `IVR-webapp` | `IVR-AI-engine` | HTTP / JSON REST | `8081` | Authentication, AI Generation, Flow Management, RAG, Analytics, Health |
| `IVR-webapp` | `IVR-payment-service` | HTTP / JSON REST | `8082` | Subscription checkout, billing status, plan retrieval, payment callbacks |
| `IVR-AI-engine` | PostgreSQL (`neondb`) | JDBC (SSL) | `5432` | Tenant data, users, flows, RAG embeddings, call logs, audit logs, notifications |
| `IVR-payment-service` | PostgreSQL (`neondb`) | JDBC (SSL) | `5432` | Subscription plans, transaction logs, tenant subscription status |
| `IVR-engine` | PostgreSQL (`neondb`) | JDBC (SSL) | `5432` | Call analytics recording, session execution tracking |
| `IVR-AI-engine` | OpenRouter / Gemini / Groq / Ollama | HTTP / REST JSON | Remote / `11434` | Generative AI pipeline execution, RAG embeddings, voice prompt suggestions |
| `IVR-payment-service` | Paymob API | HTTPS / REST JSON | `api.paymob.com` | Authentication tokens, order registration, payment key generation |
| `Asterisk PBX` | `IVR-engine` | FastAGI | `4573` (TCP) | Executing VXML scenarios on active telephony calls |
| `IVR-AI-engine` | `Asterisk PBX` | AMI Socket / Host Script | Socket / Bash | Health probing, extensions reload (`add_extension.sh`), Asterisk configuration |
| `IVR-AI-engine` | `IVR-engine` | Shared Volume | File System (`/app/IVR-engine/scenarios`) | Exporting published VXML scenario files and draft JSON flows |

---

## Repository Directory Map

### Top-Level Modules & Directory Tree

```
IVR-Platform/
├── Database/                         # SQL Migration Scripts
│   ├── AI-database/                  # AI Engine, Tenant, Telephony, Audit, & Notification Migrations (000-015)
│   ├── Payment-database/             # Payment and Subscription Migration Script (001)
│   └── voice_prompts.sql             # Legacy/Standalone Voice Prompts Table Script
├── Documentation/                    # Complete Up-to-Date Technical Documentation Package
├── IVR-AI-engine/                    # Main REST API Backend Service (Java 21 / Maven / Jakarta Servlet 6.0)
│   ├── src/main/java/com/nexusivr/ai/
│   │   ├── ai/                       # ProviderManager, CircuitBreaker, Gemini/Groq/OpenRouter Clients, Agents
│   │   ├── config/                   # Servlet Context Listener, CORS Filter, Application Properties
│   │   ├── controller/               # REST API Servlets (Auth, Flow, AI, Health, Queues, Prompts, etc.)
│   │   ├── dao/                      # Data Access Objects (PostgreSQL SQL Queries)
│   │   ├── dto/                      # Data Transfer Objects (Requests, Responses, Common, Patches)
│   │   ├── model/                    # Domain Entities & Flow Node Graphs
│   │   ├── security/                 # Password Hashing (SHA-256) & JWT Verification/Generation
│   │   ├── service/                  # Business Logic Orchestrators (AI 7-pass pipeline, VXML, RAG, etc.)
│   │   └── util/                     # XML Formatting, Sound Directory utilities
│   ├── Dockerfile
│   └── pom.xml
├── IVR-engine/                       # FastAGI Telephony Execution Engine (Java 21 / Asterisk-Java / JVoiceXML)
│   ├── src/main/java/gov/iti/telecom/
│   │   ├── api/                      # Mock/Helper API Servers
│   │   ├── dao/                      # Call Analytics DAO
│   │   ├── platform/                 # Custom JVoiceXML Platform (Asterisk Telephony, Audio, Spoken Input)
│   │   ├── App.java                  # Standalone CLI Entrypoint
│   │   ├── AnalyticsTracker.java     # Call Analytics Logger
│   │   ├── FastAgiServerMain.java    # FastAGI Server Listener Entrypoint (Port 4573)
│   │   ├── OllamaAgent.java          # Fallback AI Voice Agent
│   │   ├── TtsEngine.java            # System TTS synthesizer
│   │   ├── VxmlAgiHandler.java       # FastAGI Request Handler & Scenario Executor
│   │   ├── VxmlLoader.java           # VXML Scenario Cache & File Loader
│   │   ├── VxmlScenarioEngine.java   # JVoiceXML Engine wrapper
│   │   ├── VxmlSession.java          # VXML Session State Manager
│   │   └── VxmlValidator.java        # VXML XML Schema Validator
│   ├── draft/                        # Saved Draft IVR Flow JSON Files
│   ├── scenarios/                    # Exported Published VXML Scenario Files & JSON Definitions
│   ├── add_extension.sh              # Asterisk Dialplan Auto-Provisioning Script
│   ├── Dockerfile
│   └── pom.xml
├── IVR-payment-service/              # Paymob Subscription REST API (Java 21 / Maven / Jakarta Servlet 6.0)
│   ├── src/main/java/com/nexusivr/payment/
│   │   ├── config/                   # Servlet Context Listener, Paymob Config, Renewal Scheduler
│   │   ├── controller/               # HealthServlet, PaymentServlet
│   │   ├── dao/                      # SubscriptionPlanDao, TransactionDao, DatabaseManager
│   │   ├── model/                    # SubscriptionPlan, Transaction
│   │   ├── security/                 # JwtUtil for JWT decoding
│   │   ├── BillingData.java          # Paymob Billing Request Object
│   │   ├── HmacVerifier.java         # Paymob Callback SHA-512 HMAC Hash Verifier
│   │   ├── PaymobHttpClient.java     # Paymob REST API Integration Client
│   │   └── PaymentService.java       # Core Payment Orchestrator
│   ├── Dockerfile
│   └── pom.xml
├── IVR-webapp/                       # Frontend Web Application (React 19 + TypeScript + Vite + Tailwind/CSS)
│   ├── src/
│   │   ├── api/                      # Axios API Clients (`aiApi.ts`, `backendUrl.ts`)
│   │   ├── components/               # Layouts, Stepper, Notifications, Quota Banners, Account Menu
│   │   ├── hooks/                    # Custom React Hooks (`useAIAssistant.ts`)
│   │   ├── ivr/                      # Visual Canvas, Graph Engine, Node Config, VXML Exporter
│   │   ├── screens/                  # Page Screens (Dashboard, IVR Builder, Companies, Subscriptions, etc.)
│   │   ├── App.tsx                   # Main Router & Scoped Routes
│   │   └── main.tsx                  # React Entrypoint
│   ├── Dockerfile
│   ├── nginx.conf
│   └── package.json
├── asterisk-sounds/                  # Shared Audio & TTS Prompt File Storage
├── asterisk-cdr/                     # Asterisk Call Detail Record CSV Output (`Master.csv`)
├── tools/                            # Developer Utility Scripts (`agi_sim.py`)
├── docker-compose.yml                # Full Production Multi-Container Docker Setup
└── README.md                         # Project Readme
```
