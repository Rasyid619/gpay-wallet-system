CREATE TYPE payment_status AS ENUM (
    'PENDING',
    'SUCCESS',
    'FAILED'
);

CREATE TYPE outbox_event_type AS ENUM (
    'CREDIT_WALLET_REQUESTED'
);

CREATE TYPE outbox_event_status AS ENUM (
    'PENDING',
    'PROCESSING',
    'PROCESSED',
    'FAILED'
);

CREATE TABLE topup_transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    wallet_id UUID NOT NULL,
    amount BIGINT NOT NULL,
    status payment_status NOT NULL,
    gateway_reference TEXT NULL,
    failure_reason TEXT NULL,
    idempotency_key TEXT NOT NULL,
    trace_id TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_topup_transactions_amount_positive CHECK (amount > 0)
);

CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    idempotency_key TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    response_status INTEGER NOT NULL,
    response_body JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_idempotency_keys_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT chk_payment_idempotency_keys_response_status CHECK (
        response_status >= 100
        AND response_status <= 599
    )
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    event_type outbox_event_type NOT NULL,
    aggregate_id UUID NOT NULL REFERENCES topup_transactions(id),
    payload JSONB NOT NULL,
    status outbox_event_status NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ NULL,
    last_error TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_outbox_events_retry_count_non_negative CHECK (retry_count >= 0)
);

CREATE TABLE activity_logs (
    id UUID PRIMARY KEY,
    trace_id TEXT NULL,
    user_id UUID NULL,
    transaction_id UUID NULL REFERENCES topup_transactions(id),
    action TEXT NOT NULL,
    status TEXT NOT NULL,
    request_payload JSONB NULL,
    response_payload JSONB NULL,
    duration_ms BIGINT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_outbox_events_aggregate_id_event_type
ON outbox_events (aggregate_id, event_type);

CREATE INDEX idx_topup_transactions_user_id
ON topup_transactions (user_id);

CREATE INDEX idx_topup_transactions_status
ON topup_transactions (status);

CREATE INDEX idx_topup_transactions_idempotency_key
ON topup_transactions (idempotency_key);

CREATE INDEX idx_outbox_events_status_next_retry_at
ON outbox_events (status, next_retry_at);

CREATE INDEX idx_activity_logs_trace_id
ON activity_logs (trace_id);

CREATE INDEX idx_activity_logs_user_id
ON activity_logs (user_id);

CREATE INDEX idx_activity_logs_transaction_id
ON activity_logs (transaction_id);
