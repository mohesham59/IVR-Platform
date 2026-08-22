# Database Module

## Overview
The `Database` directory contains the SQL schema definitions, migrations, and initialization scripts for the platform's PostgreSQL database infrastructure.

## Technical Responsibilities
- **Schema Definition:** Defines the relational structure for cross-cutting platform concerns, including user authentication, multitenancy, and audit logging.
- **Telephony Data:** Defines tables for call logs, telephony analytics, SIP extensions, and queues.
- **AI and Knowledge:** Structures the storage for AI chat sessions, conversation histories, prompt templates, and vector embeddings.
- **Billing Data:** Maintains schemas for tracking payment transactions and active subscription plans.

## Architectural Flow
These scripts are typically executed during the deployment or CI/CD pipeline to provision the database schema before the application services start. They ensure structural consistency and constraints (foreign keys, NOT NULL) across all microservices that rely on the database.
