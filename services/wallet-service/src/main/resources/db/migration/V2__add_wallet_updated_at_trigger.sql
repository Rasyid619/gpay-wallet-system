CREATE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_wallets_set_updated_at
BEFORE UPDATE ON wallets
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

DROP INDEX idx_transfers_sender_wallet_id_created_at;

CREATE INDEX idx_transfers_sender_wallet_id_status_created_at
ON transfers (sender_wallet_id, status, created_at DESC);
