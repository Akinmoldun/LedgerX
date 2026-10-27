CREATE TYPE transfer_status AS ENUM ('pending','posted','failed','reversed');

CREATE TABLE transfers (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id       UUID NOT NULL REFERENCES tenants(id),
  idempotency_key TEXT NOT NULL,
  status          transfer_status NOT NULL DEFAULT 'pending',
  currency        CHAR(3) NOT NULL,
  amount_minor    BIGINT NOT NULL CHECK (amount_minor > 0),
  description     TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  posted_at       TIMESTAMPTZ,
  UNIQUE (tenant_id, idempotency_key)
);
CREATE INDEX idx_transfers_tenant_created ON transfers (tenant_id, created_at DESC);

CREATE TABLE entries (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  transfer_id  UUID NOT NULL REFERENCES transfers(id),
  account_id   UUID NOT NULL REFERENCES accounts(id),
  direction    TEXT NOT NULL CHECK (direction IN ('debit','credit')),
  amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (transfer_id, account_id, direction)
);
CREATE INDEX idx_entries_account ON entries (account_id, created_at DESC);
CREATE INDEX idx_entries_transfer ON entries (transfer_id);

CREATE OR REPLACE FUNCTION check_transfer_balanced() RETURNS trigger AS $$
DECLARE d BIGINT; c BIGINT;
BEGIN
  SELECT
    COALESCE(SUM(amount_minor) FILTER (WHERE direction='debit'),0),
    COALESCE(SUM(amount_minor) FILTER (WHERE direction='credit'),0)
  INTO d, c FROM entries WHERE transfer_id = NEW.transfer_id;
  IF d <> c THEN
    RAISE EXCEPTION 'unbalanced transfer %: debit=% credit=%', NEW.transfer_id, d, c;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER entries_balanced
AFTER INSERT ON entries
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION check_transfer_balanced();
