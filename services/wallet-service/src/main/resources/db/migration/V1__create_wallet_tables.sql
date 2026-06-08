CREATE TYPE wallet_status AS ENUM (
    'ACTIVE',
    'SUSPENDED'
);

CREATE TYPE ledger_entry_type AS ENUM (
    'DEBIT',
    'CREDIT'
);

CREATE TYPE ledger_entry_source AS ENUM (
    'TRANSFER',
    'TOP_UP'
);

CREATE TYPE transfer_status AS ENUM (
    'SUCCESS',
    'FAILED'
);

CREATE TABLE wallets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    balance BIGINT NOT NULL DEFAULT 0,
    status wallet_status NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_wallets_balance_non_negative CHECK (balance >= 0)
);

CREATE TABLE transfers (
    id UUID PRIMARY KEY,
    sender_wallet_id UUID NOT NULL REFERENCES wallets(id),
    receiver_wallet_id UUID NOT NULL REFERENCES wallets(id),
    amount BIGINT NOT NULL,
    status transfer_status NOT NULL,
    failure_reason TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_transfers_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_transfers_distinct_wallets CHECK (sender_wallet_id <> receiver_wallet_id)
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    transfer_id UUID NULL REFERENCES transfers(id),
    payment_transaction_id UUID NULL,
    entry_type ledger_entry_type NOT NULL,
    source ledger_entry_source NOT NULL,
    amount BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    description TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_ledger_entries_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_ledger_entries_balance_after_non_negative CHECK (balance_after >= 0),
    CONSTRAINT chk_ledger_entries_source_reference CHECK (
        (source = 'TRANSFER' AND transfer_id IS NOT NULL AND payment_transaction_id IS NULL)
        OR (source = 'TOP_UP' AND transfer_id IS NULL AND payment_transaction_id IS NOT NULL)
    )
);

CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY,
    idempotency_key TEXT NOT NULL,
    user_id UUID NULL,
    request_hash TEXT NOT NULL,
    response_status INTEGER NOT NULL,
    response_body JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_idempotency_keys_key UNIQUE (idempotency_key),
    CONSTRAINT chk_idempotency_keys_response_status CHECK (
        response_status >= 100
        AND response_status <= 599
    )
);

CREATE TABLE activity_logs (
    id UUID PRIMARY KEY,
    user_id UUID NULL,
    wallet_id UUID NULL REFERENCES wallets(id),
    action TEXT NOT NULL,
    status TEXT NOT NULL,
    trace_id TEXT NULL,
    metadata JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_ledger_entries_payment_transaction_id
ON ledger_entries (payment_transaction_id)
WHERE payment_transaction_id IS NOT NULL;

CREATE INDEX idx_wallets_user_id
ON wallets (user_id);

CREATE INDEX idx_transfers_sender_wallet_id_created_at
ON transfers (sender_wallet_id, created_at DESC);

CREATE INDEX idx_transfers_receiver_wallet_id_created_at
ON transfers (receiver_wallet_id, created_at DESC);

CREATE INDEX idx_ledger_entries_wallet_id_created_at
ON ledger_entries (wallet_id, created_at DESC);

CREATE INDEX idx_activity_logs_user_id_created_at
ON activity_logs (user_id, created_at DESC);
