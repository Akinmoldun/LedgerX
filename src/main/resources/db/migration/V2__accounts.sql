CREATE TYPE account_type AS ENUM ('asset','liability','equity','revenue','expense');

CREATE TABLE accounts (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id       UUID NOT NULL REFERENCES tenants(id),
  code            TEXT NOT NULL,
  name            TEXT NOT NULL,
  type            account_type NOT NULL,
  currency        CHAR(3) NOT NULL,
  balance_minor   BIGINT NOT NULL DEFAULT 0,
  version         BIGINT NOT NULL DEFAULT 0,
  allow_overdraft BOOLEAN NOT NULL DEFAULT false,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, code),
  CHECK (allow_overdraft OR balance_minor >= 0)
);
CREATE INDEX idx_accounts_tenant ON accounts (tenant_id);
