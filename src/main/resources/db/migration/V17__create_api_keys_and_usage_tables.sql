-- V17: Create api_keys and api_usage tables for machine-to-machine auth + rate limiting
-- Only the SHA-256 hash of the secret is stored; the full key is shown once at creation.

CREATE TABLE api_keys
(
    id               UUID PRIMARY KEY       DEFAULT gen_random_uuid(),
    user_id          UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name             VARCHAR(100)  NOT NULL,
    prefix           VARCHAR(16)   NOT NULL,
    key_hash         VARCHAR(64)   NOT NULL UNIQUE,
    scopes           JSONB         NOT NULL DEFAULT '[]',
    rate_limit_per_min INT         DEFAULT 60,
    expires_at       TIMESTAMPTZ,
    last_used_at     TIMESTAMPTZ,
    is_active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_api_keys_lookup ON api_keys (prefix, is_active);

CREATE TABLE api_usage
(
    id                UUID PRIMARY KEY       DEFAULT gen_random_uuid(),
    api_key_id        UUID          NOT NULL REFERENCES api_keys (id) ON DELETE CASCADE,
    endpoint          VARCHAR(255)  NOT NULL,
    method            VARCHAR(16)   NOT NULL,
    status_code       INT           NOT NULL,
    response_time_ms  INT           NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_api_usage_key ON api_usage (api_key_id, created_at);
