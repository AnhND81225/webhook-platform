ALTER TABLE webhook_deliveries
    DROP CONSTRAINT ck_webhook_deliveries_status,
    ADD COLUMN processing_started_at TIMESTAMPTZ NULL,
    ADD COLUMN claim_token UUID NULL,
    ADD CONSTRAINT ck_webhook_deliveries_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'DELIVERED', 'FAILED')),
    ADD CONSTRAINT ck_webhook_deliveries_processing_claim
        CHECK (
            (status = 'PROCESSING' AND processing_started_at IS NOT NULL AND claim_token IS NOT NULL)
            OR
            (status <> 'PROCESSING' AND processing_started_at IS NULL AND claim_token IS NULL)
        );

CREATE INDEX idx_webhook_deliveries_processing_started
    ON webhook_deliveries (processing_started_at, id)
    WHERE status = 'PROCESSING';
