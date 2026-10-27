CREATE TABLE audit_log (
  id           BIGSERIAL PRIMARY KEY,
  tenant_id    UUID NOT NULL,
  actor_id     UUID,
  action       TEXT NOT NULL,
  entity_type  TEXT NOT NULL,
  entity_id    UUID,
  before       JSONB,
  after        JSONB,
  reason       TEXT,
  request_id   UUID,
  ip           INET,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_lookup ON audit_log (tenant_id, entity_type, entity_id, created_at DESC);
