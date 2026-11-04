-- V9__demo_seed.sql
-- Seed accounts and balanced opening transfer for demo walkthrough and live testing

-- 1. Demo Operating Account (Asset)
INSERT INTO accounts (id, tenant_id, code, name, type, currency, balance_minor, allow_overdraft)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    '00000000-0000-0000-0000-000000000001',
    '1000',
    'Demo Operating Account',
    'asset',
    'USD',
    100000,
    false
);

-- 2. Demo Merchant Revenue Account (Revenue)
INSERT INTO accounts (id, tenant_id, code, name, type, currency, balance_minor, allow_overdraft)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    '00000000-0000-0000-0000-000000000001',
    '4000',
    'Demo Merchant Revenue',
    'revenue',
    'USD',
    0,
    false
);

-- 3. Initial Equity Funding Account (Equity, allow_overdraft = true)
INSERT INTO accounts (id, tenant_id, code, name, type, currency, balance_minor, allow_overdraft)
VALUES (
    '33333333-3333-3333-3333-333333333333',
    '00000000-0000-0000-0000-000000000001',
    '3000',
    'Demo Equity Funding',
    'equity',
    'USD',
    -100000,
    true
);

-- 4. Opening Balance Transfer and Offsetting Entries (Double-Entry Invariant)
INSERT INTO transfers (id, tenant_id, idempotency_key, status, currency, amount_minor, description, posted_at)
VALUES (
    '00000000-0000-0000-0000-000000000010',
    '00000000-0000-0000-0000-000000000001',
    'seed-opening-balance-demo',
    'posted',
    'USD',
    100000,
    'Demo initial seed opening balance',
    now()
);

INSERT INTO entries (id, transfer_id, account_id, direction, amount_minor)
VALUES
    ('00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000010', '33333333-3333-3333-3333-333333333333', 'debit', 100000),
    ('00000000-0000-0000-0000-000000000012', '00000000-0000-0000-0000-000000000010', '11111111-1111-1111-1111-111111111111', 'credit', 100000);
