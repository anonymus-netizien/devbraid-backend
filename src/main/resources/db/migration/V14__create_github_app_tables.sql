-- V14: GitHub App + Webhooks
-- Tracks GitHub App installations and received webhook events

CREATE TABLE github_app_installations
(
    id                UUID PRIMARY KEY     DEFAULT uuidv7(),
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    installation_id   BIGINT      NOT NULL,
    account_login     VARCHAR(255) NOT NULL,
    account_type      VARCHAR(50)  NOT NULL DEFAULT 'User',
    repository_selection VARCHAR(50) NOT NULL DEFAULT 'all',
    access_token_encrypted BYTEA,
    access_token_iv   BYTEA,
    permissions       JSONB,
    events            JSONB,
    installed_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    suspended_at      TIMESTAMPTZ,
    UNIQUE (installation_id)
);

CREATE INDEX idx_github_app_installations_user_id ON github_app_installations (user_id);
CREATE INDEX idx_github_app_installations_installation_id ON github_app_installations (installation_id);

CREATE TABLE github_webhooks
(
    id                UUID PRIMARY KEY     DEFAULT uuidv7(),
    installation_id   BIGINT      NOT NULL,
    event_type        VARCHAR(100) NOT NULL,
    action            VARCHAR(100),
    delivery_id       VARCHAR(255) UNIQUE,
    payload           JSONB       NOT NULL,
    processed         BOOLEAN     NOT NULL DEFAULT FALSE,
    processing_error  TEXT,
    received_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at      TIMESTAMPTZ
);

CREATE INDEX idx_github_webhooks_installation_id ON github_webhooks (installation_id);
CREATE INDEX idx_github_webhooks_event_type ON github_webhooks (event_type);
CREATE INDEX idx_github_webhooks_processed ON github_webhooks (processed);
CREATE INDEX idx_github_webhooks_delivery_id ON github_webhooks (delivery_id);
