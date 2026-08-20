# IVR-payment-service Module

## Overview
The `IVR-payment-service` directory is a dedicated microservice for handling billing, subscriptions, and payment gateway integrations.

## Technical Responsibilities
- **Payment Gateway Integration:** Interfaces securely with third-party payment providers (like Paymob) to initiate and verify transactions.
- **Subscription Management:** Tracks tenant subscription tiers, billing cycles, and quotas.
- **Webhook Handling:** Listens for asynchronous callbacks from the payment gateway to finalize transaction statuses.

## Architectural Flow
Deployed as a standalone Java microservice, it operates independently from the main API engine to isolate sensitive billing logic. It interacts directly with the database to update transaction records and tenant states based on successful or failed payments.
