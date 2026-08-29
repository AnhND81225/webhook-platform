CREATE TABLE webhook_events (
    id UUID NOT NULL,
    application_id UUID NOT NULL,
    source_event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_webhook_events PRIMARY KEY (id),
    CONSTRAINT fk_webhook_events_application FOREIGN KEY (application_id)
        REFERENCES applications(id) ON DELETE RESTRICT,
    CONSTRAINT uq_webhook_events_application_source_event UNIQUE (application_id, source_event_id),
    CONSTRAINT ck_webhook_events_source_event_id CHECK (
        source_event_id = BTRIM(source_event_id) AND CHAR_LENGTH(source_event_id) BETWEEN 1 AND 255
    ),
    CONSTRAINT ck_webhook_events_event_type CHECK (
        event_type ~ '^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$'
    ),
    CONSTRAINT ck_webhook_events_payload_object CHECK (jsonb_typeof(payload) = 'object')
);

CREATE INDEX idx_webhook_events_application_created
    ON webhook_events (application_id, created_at DESC, id DESC);
