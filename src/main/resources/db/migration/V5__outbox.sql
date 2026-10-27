CREATE TABLE outbox (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  aggregate_type TEXT NOT NULL,
  aggregate_id   UUID NOT NULL,
  event_type     TEXT NOT NULL,
  payload        JSONB NOT NULL,
  headers        JSONB NOT NULL DEFAULT '{}',
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at   TIMESTAMPTZ,
  attempts       INT NOT NULL DEFAULT 0,
  last_error     TEXT
);
CREATE INDEX outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;
