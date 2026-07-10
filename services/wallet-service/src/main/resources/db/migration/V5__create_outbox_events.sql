CREATE TYPE outbox_event_type AS ENUM (
    'TRANSFER_COMPLETED',
    'TRANSFER_RECEIVED',
    'TRANSFER_FAILED'
);

CREATE TYPE outbox_event_status AS ENUM (
    'PENDING',
    'PROCESSING',
    'PROCESSED',
    'FAILED'
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    event_type outbox_event_type NOT NULL,
    aggregate_id UUID NOT NULL REFERENCES transfers(id),
    payload JSONB NOT NULL,
    status outbox_event_status NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ NULL,
    last_error TEXT NULL,
    trace_id TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_outbox_events_retry_count_non_negative CHECK (retry_count >= 0)
);

CREATE UNIQUE INDEX uq_outbox_events_aggregate_id_event_type
ON outbox_events (aggregate_id, event_type);

CREATE INDEX idx_outbox_events_status_next_retry_at
ON outbox_events (status, next_retry_at);
