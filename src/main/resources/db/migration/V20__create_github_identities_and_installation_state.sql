-- V20: GitHub OAuth identity linking + installation lifecycle state
-- github_identities links a DevBraid user to a GitHub user id so GitHub App
-- installations can be attributed to DevBraid users.

CREATE TABLE github_identities
(
    id             UUID PRIMARY KEY      DEFAULT uuidv7(),
    user_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    github_user_id BIGINT       NOT NULL UNIQUE,
    github_login   VARCHAR(255),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_github_identities_user_id ON github_identities (user_id);

-- account_login already exists since V14 — IF NOT EXISTS keeps this idempotent.
ALTER TABLE github_app_installations
    ADD COLUMN IF NOT EXISTS account_login VARCHAR(255),
    ADD COLUMN IF NOT EXISTS active        BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS suspended     BOOLEAN      NOT NULL DEFAULT FALSE;
