-- ============================================================================
-- NexusIVR Payment & Subscriptions Schema
-- PostgreSQL 15+
-- Tables: subscription_plans, transactions
-- Alterations: tenants (subscription_plan_id, subscription_status, subscription_expires_at)
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. subscription_plans
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS subscription_plans (
    id                UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    name              VARCHAR(100) UNIQUE NOT NULL,
    price_piasters    BIGINT NOT NULL,
    billing_interval  VARCHAR(20) NOT NULL,
    integration_ids   TEXT,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- 2. transactions
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS transactions (
    id                    UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id             UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    type                  VARCHAR(20) NOT NULL CHECK (type IN ('SUBSCRIPTION', 'ONE_TIME')),
    amount_piasters       BIGINT NOT NULL,
    currency              VARCHAR(10) NOT NULL DEFAULT 'EGP',
    status                VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'CANCELLED', 'EXPIRED')),
    paymob_transaction_id VARCHAR(100),
    paymob_order_id       VARCHAR(100),
    plan_id               UUID REFERENCES subscription_plans(id) ON DELETE SET NULL,
    card_token            VARCHAR(255),
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_transactions_tenant_id ON transactions (tenant_id);
CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions (status);
CREATE INDEX IF NOT EXISTS idx_transactions_paymob_txn ON transactions (paymob_transaction_id);

-- ---------------------------------------------------------------------------
-- 3. Alter tenants table idempotently
-- ---------------------------------------------------------------------------
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS subscription_plan_id UUID REFERENCES subscription_plans(id) ON DELETE SET NULL;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS subscription_status VARCHAR(20) DEFAULT 'INACTIVE';
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS subscription_expires_at TIMESTAMP WITH TIME ZONE;

-- ---------------------------------------------------------------------------
-- 4. Seed default subscription plans (500 EGP, 1500 EGP, 5000 EGP in piasters)
-- ---------------------------------------------------------------------------
INSERT INTO subscription_plans (id, name, price_piasters, billing_interval)
VALUES 
    ('b1000000-0000-0000-0000-000000000001', 'Starter', 50000, 'MONTHLY'),
    ('b1000000-0000-0000-0000-000000000002', 'Business', 150000, 'MONTHLY'),
    ('b1000000-0000-0000-0000-000000000003', 'Enterprise', 500000, 'MONTHLY')
ON CONFLICT DO NOTHING;
