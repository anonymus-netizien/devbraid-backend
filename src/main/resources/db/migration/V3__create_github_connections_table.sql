CREATE TABLE github_connections
(
    id              UUID PRIMARY KEY     DEFAULT uuidv7(),
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    encrypted_pat   BYTEA       NOT NULL,
    iv              BYTEA       NOT NULL,
    github_username VARCHAR(255),
    connected_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_validated  TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_github_connections_user_id ON github_connections (user_id);
