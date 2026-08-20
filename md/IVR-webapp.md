# IVR-webapp Module

## Overview
The `IVR-webapp` directory contains the frontend application for the Nexus IVR Platform. It provides the graphical user interface for administrators to interact with the system.

## Technical Responsibilities
- **User Interface Rendering:** Delivers the web-based dashboards for Super Admins (platform oversight) and Tenant Admins (client-specific management).
- **Visual Flow Builder:** Implements a drag-and-drop canvas for designing Interactive Voice Response (IVR) scenarios and exporting them to VXML.
- **System Configuration:** Provides interfaces for managing SIP extensions, uploading voice prompts, configuring queues, and monitoring system health.
- **Analytics Visualization:** Displays real-time call analytics, audit logs, and billing reports.

## Architectural Flow
The web application runs entirely in the client's browser, communicating with the backend `IVR-AI-engine` and `IVR-payment-service` via REST APIs. It relies on standard modern web paradigms, utilizing component-based architecture for UI modularity and client-side routing for navigation.
