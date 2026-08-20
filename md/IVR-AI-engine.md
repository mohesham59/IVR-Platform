# IVR-AI-engine Module

## Overview
The `IVR-AI-engine` directory contains the primary backend application. It serves as both the REST API provider for the frontend and the Artificial Intelligence orchestrator.

## Technical Responsibilities
- **API Gateway:** Exposes HTTP endpoints (Servlets) for the frontend to manage users, tenants, queues, and SIP extensions.
- **AI Orchestration:** Integrates with external LLM providers (e.g., OpenRouter, Gemini, Groq) to provide AI-assisted IVR generation, validation, and auto-correction.
- **Agentic Architecture:** Utilizes specialized internal agents (e.g., ValidatorAssistant, BusinessPlanner, RoutingExpert) to modularize complex AI tasks.
- **RAG Integration:** Communicates with the separate RAG microservice to perform semantic searches over knowledge documents.
- **Dialplan Automation:** Executes local bash scripts (like `add_extension.sh`) to dynamically modify the Asterisk dialplan when scenarios are published.

## Architectural Flow
The module runs within a Java Servlet container (like Tomcat or Jetty). It receives stateless REST requests from the webapp, processes business logic, interacts with the PostgreSQL database, and coordinates outbound HTTP requests to AI providers. It also includes a Python-based `rag` sub-module (often deployed as `ivr-rag-service`) that wraps ChromaDB for vector similarity searches.
