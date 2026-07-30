CREATE TABLE users
(
    id            UUID PRIMARY KEY             DEFAULT uuidv7(),
    email         VARCHAR(255) UNIQUE NOT NULL,
    full_name     VARCHAR(255),
    password_hash VARCHAR(255)        NOT NULL,
    created_at    TIMESTAMPTZ         NOT NULL DEFAULT CURRENT_TIMESTAMP
);