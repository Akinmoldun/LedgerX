CREATE TABLE ledger_events (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      UUID NOT NULL,
  aggregate_type TEXT NOT NULL,
  aggregate_id   UUID NOT NULL,
  sequence       BIGINT NOT NULL,
  event_type     TEXT NOT NULL,
  payload        JSONB NOT NULL,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (aggregate_type, aggregate_id, sequence)
);
CREATE INDEX idx_ledger_events_tenant ON ledger_events (tenant_id, created_at);
CREATE INDEX idx_ledger_events_agg ON ledger_events (aggregate_type, aggregate_id, sequence);

CREATE OR REPLACE FUNCTION deny_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'append-only table %: % not allowed', TG_TABLE_NAME, TG_OP;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_events_no_update BEFORE UPDATE ON ledger_events
  FOR EACH ROW EXECUTE FUNCTION deny_mutation();
CREATE TRIGGER ledger_events_no_delete BEFORE DELETE ON ledger_events
  FOR EACH ROW EXECUTE FUNCTION deny_mutation();
