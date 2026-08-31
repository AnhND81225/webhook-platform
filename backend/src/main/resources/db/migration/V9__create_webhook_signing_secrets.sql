CREATE TABLE webhook_signing_secrets (
    id UUID PRIMARY KEY,
    endpoint_id UUID NOT NULL UNIQUE,
    encrypted_secret BYTEA NOT NULL,
    nonce BYTEA NOT NULL,
    key_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_webhook_signing_secrets_endpoint
        FOREIGN KEY (endpoint_id) REFERENCES webhook_endpoints(id) ON DELETE RESTRICT,
    CONSTRAINT ck_webhook_signing_secrets_nonce_length CHECK (octet_length(nonce) = 12),
    CONSTRAINT ck_webhook_signing_secrets_key_version CHECK (key_version > 0)
);

ALTER TABLE webhook_delivery_attempts
    DROP CONSTRAINT ck_webhook_delivery_attempts_lifecycle,
    ADD CONSTRAINT ck_webhook_delivery_attempts_lifecycle CHECK (
        (status = 'IN_PROGRESS' AND completed_at IS NULL AND duration_ms IS NULL AND http_status_code IS NULL AND error_code IS NULL)
        OR (status = 'SUCCEEDED' AND completed_at IS NOT NULL AND duration_ms IS NOT NULL
            AND http_status_code BETWEEN 200 AND 299 AND error_code IS NULL)
        OR (status = 'FAILED' AND completed_at IS NOT NULL AND duration_ms IS NOT NULL AND (
            (http_status_code IS NOT NULL AND (http_status_code < 200 OR http_status_code > 299) AND error_code = 'HTTP_ERROR')
            OR (http_status_code IS NULL AND error_code IN ('DNS_ERROR', 'SSRF_REJECTED', 'CONNECTION_ERROR', 'TIMEOUT', 'TLS_ERROR', 'IO_ERROR', 'UNEXPECTED_ERROR', 'SIGNING_ERROR'))
        ))
        OR (status = 'ABANDONED' AND completed_at IS NOT NULL AND duration_ms IS NULL AND http_status_code IS NULL AND error_code IS NULL)
    );
