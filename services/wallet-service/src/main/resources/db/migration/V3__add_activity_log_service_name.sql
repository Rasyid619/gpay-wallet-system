ALTER TABLE activity_logs
ADD COLUMN service_name TEXT NOT NULL DEFAULT 'wallet-service';
