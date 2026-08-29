CREATE TABLE applications (
    id UUID NOT NULL,
    owner_user_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(63) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    environment VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_applications PRIMARY KEY (id),
    CONSTRAINT fk_applications_owner FOREIGN KEY (owner_user_id)
        REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uq_applications_owner_slug UNIQUE (owner_user_id, slug),
    CONSTRAINT ck_applications_name CHECK (
        name = BTRIM(name) AND CHAR_LENGTH(name) BETWEEN 1 AND 120
    ),
    CONSTRAINT ck_applications_slug CHECK (
        slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'
    ),
    CONSTRAINT ck_applications_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_applications_environment CHECK (environment IN ('DEVELOPMENT', 'PRODUCTION'))
);

CREATE INDEX idx_applications_owner_created
    ON applications (owner_user_id, created_at DESC, id DESC);

CREATE TABLE api_keys (
    id UUID NOT NULL,
    application_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    key_prefix VARCHAR(20) NOT NULL,
    key_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    last_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ,

    CONSTRAINT pk_api_keys PRIMARY KEY (id),
    CONSTRAINT fk_api_keys_application FOREIGN KEY (application_id)
        REFERENCES applications(id) ON DELETE RESTRICT,
    CONSTRAINT uq_api_keys_key_hash UNIQUE (key_hash),
    CONSTRAINT ck_api_keys_name CHECK (
        name = BTRIM(name) AND CHAR_LENGTH(name) BETWEEN 1 AND 120
    ),
    CONSTRAINT ck_api_keys_hash CHECK (key_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_api_keys_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_api_keys_revocation CHECK (
        (status = 'ACTIVE' AND revoked_at IS NULL)
        OR (status = 'REVOKED' AND revoked_at IS NOT NULL)
    )
);

CREATE INDEX idx_api_keys_application_created
    ON api_keys (application_id, created_at DESC, id DESC);
