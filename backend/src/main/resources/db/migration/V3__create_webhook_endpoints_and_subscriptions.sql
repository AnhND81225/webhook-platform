CREATE TABLE webhook_endpoints (
    id UUID NOT NULL,
    application_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_webhook_endpoints PRIMARY KEY (id),
    CONSTRAINT fk_webhook_endpoints_application FOREIGN KEY (application_id)
        REFERENCES applications(id) ON DELETE RESTRICT,
    CONSTRAINT ck_webhook_endpoints_name CHECK (
        name = BTRIM(name) AND CHAR_LENGTH(name) BETWEEN 1 AND 120
    ),
    CONSTRAINT ck_webhook_endpoints_url CHECK (
        url = BTRIM(url) AND CHAR_LENGTH(url) BETWEEN 1 AND 2048
    ),
    CONSTRAINT ck_webhook_endpoints_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_webhook_endpoints_application_created
    ON webhook_endpoints (application_id, created_at DESC, id DESC);

CREATE TABLE webhook_subscriptions (
    id UUID NOT NULL,
    endpoint_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_webhook_subscriptions PRIMARY KEY (id),
    CONSTRAINT fk_webhook_subscriptions_endpoint FOREIGN KEY (endpoint_id)
        REFERENCES webhook_endpoints(id) ON DELETE RESTRICT,
    CONSTRAINT uq_webhook_subscriptions_endpoint_event_type UNIQUE (endpoint_id, event_type),
    CONSTRAINT ck_webhook_subscriptions_event_type CHECK (
        event_type ~ '^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$'
    )
);

CREATE INDEX idx_webhook_subscriptions_event_type_endpoint
    ON webhook_subscriptions (event_type, endpoint_id);
