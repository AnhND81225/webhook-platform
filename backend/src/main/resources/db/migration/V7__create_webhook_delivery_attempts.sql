CREATE TABLE webhook_delivery_attempts (
    id UUID NOT NULL,
    delivery_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    claim_token UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NULL,
    duration_ms BIGINT NULL,
    http_status_code INTEGER NULL,
    error_code VARCHAR(32) NULL,
    CONSTRAINT pk_webhook_delivery_attempts PRIMARY KEY (id),
    CONSTRAINT fk_webhook_delivery_attempts_delivery FOREIGN KEY (delivery_id)
        REFERENCES webhook_deliveries(id) ON DELETE RESTRICT,
    CONSTRAINT uq_webhook_delivery_attempts_delivery_number UNIQUE (delivery_id, attempt_number),
    CONSTRAINT uq_webhook_delivery_attempts_delivery_claim UNIQUE (delivery_id, claim_token),
    CONSTRAINT ck_webhook_delivery_attempts_number CHECK (attempt_number > 0),
    CONSTRAINT ck_webhook_delivery_attempts_duration CHECK (duration_ms IS NULL OR duration_ms >= 0),
    CONSTRAINT ck_webhook_delivery_attempts_http_status CHECK (http_status_code IS NULL OR http_status_code BETWEEN 100 AND 599),
    CONSTRAINT ck_webhook_delivery_attempts_status CHECK (status IN ('IN_PROGRESS', 'SUCCEEDED', 'FAILED', 'ABANDONED')),
    CONSTRAINT ck_webhook_delivery_attempts_lifecycle CHECK (
        (status = 'IN_PROGRESS' AND completed_at IS NULL AND duration_ms IS NULL AND http_status_code IS NULL AND error_code IS NULL)
        OR (status = 'SUCCEEDED' AND completed_at IS NOT NULL AND duration_ms IS NOT NULL
            AND http_status_code BETWEEN 200 AND 299 AND error_code IS NULL)
        OR (status = 'FAILED' AND completed_at IS NOT NULL AND duration_ms IS NOT NULL AND (
            (http_status_code IS NOT NULL AND (http_status_code < 200 OR http_status_code > 299) AND error_code = 'HTTP_ERROR')
            OR (http_status_code IS NULL AND error_code IN ('DNS_ERROR', 'SSRF_REJECTED', 'CONNECTION_ERROR', 'TIMEOUT', 'TLS_ERROR', 'IO_ERROR', 'UNEXPECTED_ERROR'))
        ))
        OR (status = 'ABANDONED' AND completed_at IS NOT NULL AND duration_ms IS NULL AND http_status_code IS NULL AND error_code IS NULL)
    )
);

CREATE INDEX idx_webhook_delivery_attempts_delivery_history
    ON webhook_delivery_attempts (delivery_id, attempt_number DESC);

CREATE INDEX idx_webhook_delivery_attempts_in_progress_claim
    ON webhook_delivery_attempts (claim_token, delivery_id)
    WHERE status = 'IN_PROGRESS';
