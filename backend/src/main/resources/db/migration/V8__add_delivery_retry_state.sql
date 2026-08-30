ALTER TABLE webhook_deliveries
    DROP CONSTRAINT ck_webhook_deliveries_processing_claim,
    DROP CONSTRAINT ck_webhook_deliveries_status,
    ADD COLUMN next_retry_at TIMESTAMPTZ NULL,
    ADD CONSTRAINT ck_webhook_deliveries_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY_SCHEDULED', 'DELIVERED', 'FAILED')),
    ADD CONSTRAINT ck_webhook_deliveries_state
        CHECK (
            (status = 'PROCESSING' AND processing_started_at IS NOT NULL AND claim_token IS NOT NULL AND next_retry_at IS NULL)
            OR (status = 'RETRY_SCHEDULED' AND processing_started_at IS NULL AND claim_token IS NULL AND next_retry_at IS NOT NULL)
            OR (status IN ('PENDING', 'DELIVERED', 'FAILED') AND processing_started_at IS NULL AND claim_token IS NULL AND next_retry_at IS NULL)
        );

CREATE INDEX idx_webhook_deliveries_retry_scheduled_due
    ON webhook_deliveries (next_retry_at, id)
    WHERE status = 'RETRY_SCHEDULED';
