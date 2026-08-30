CREATE TABLE webhook_deliveries (
    id UUID NOT NULL,
    event_id UUID NOT NULL,
    endpoint_id UUID NOT NULL,
    target_url VARCHAR(2048) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_webhook_deliveries PRIMARY KEY (id),
    CONSTRAINT fk_webhook_deliveries_event FOREIGN KEY (event_id) REFERENCES webhook_events(id) ON DELETE RESTRICT,
    CONSTRAINT fk_webhook_deliveries_endpoint FOREIGN KEY (endpoint_id) REFERENCES webhook_endpoints(id) ON DELETE RESTRICT,
    CONSTRAINT uq_webhook_deliveries_event_endpoint UNIQUE (event_id, endpoint_id),
    CONSTRAINT ck_webhook_deliveries_target_url CHECK (target_url = BTRIM(target_url) AND CHAR_LENGTH(target_url) BETWEEN 1 AND 2048),
    CONSTRAINT ck_webhook_deliveries_status CHECK (status = 'PENDING')
);
CREATE INDEX idx_webhook_deliveries_pending_created ON webhook_deliveries (created_at, id) WHERE status = 'PENDING';
