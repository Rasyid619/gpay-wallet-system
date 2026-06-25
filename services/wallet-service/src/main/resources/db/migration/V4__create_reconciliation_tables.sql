CREATE TYPE reconciliation_run_status AS ENUM (
    'STARTED',
    'COMPLETED',
    'FAILED'
);

CREATE TABLE wallet_reconciliation_runs (
    id UUID PRIMARY KEY,
    status reconciliation_run_status NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NULL,
    checked_wallet_count INTEGER NOT NULL DEFAULT 0,
    mismatch_count INTEGER NOT NULL DEFAULT 0,
    duration_ms BIGINT NULL,
    CONSTRAINT chk_wallet_reconciliation_runs_checked_count_non_negative CHECK (checked_wallet_count >= 0),
    CONSTRAINT chk_wallet_reconciliation_runs_mismatch_count_non_negative CHECK (mismatch_count >= 0)
);

CREATE TABLE wallet_reconciliation_mismatches (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES wallet_reconciliation_runs(id),
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    stored_balance BIGINT NOT NULL,
    ledger_balance BIGINT NOT NULL,
    difference BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_wallet_reconciliation_runs_started_at
ON wallet_reconciliation_runs (started_at DESC);

CREATE INDEX idx_wallet_reconciliation_mismatches_run_id
ON wallet_reconciliation_mismatches (run_id);
