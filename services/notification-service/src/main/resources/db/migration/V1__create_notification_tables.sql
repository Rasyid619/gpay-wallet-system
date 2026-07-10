CREATE TYPE notification_type AS ENUM (
    'TRANSFER_COMPLETED',
    'TRANSFER_RECEIVED',
    'TRANSFER_FAILED',
    'TOPUP_SUCCEEDED',
    'TOPUP_FAILED'
);

CREATE TYPE notification_status AS ENUM (
    'PENDING',
    'SENT',
    'FAILED'
);

CREATE TABLE notification_attempts (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    notification_type notification_type NOT NULL,
    recipient_email TEXT NOT NULL,
    status notification_status NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    trace_id TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMPTZ NULL,
    CONSTRAINT uq_notification_attempts_event_id UNIQUE (event_id),
    CONSTRAINT chk_notification_attempts_retry_count_non_negative CHECK (retry_count >= 0)
);

CREATE INDEX idx_notification_attempts_status
ON notification_attempts (status);
