ALTER TABLE idempotency_records
    MODIFY request_hash VARCHAR(64) NOT NULL;
