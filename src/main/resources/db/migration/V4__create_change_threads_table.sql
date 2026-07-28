CREATE TABLE change_threads
(
    id                      UUID PRIMARY KEY DEFAULT uuidv7(),
    user_id                 UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    repository_full_name    VARCHAR(500) NOT NULL,
    head_branch             VARCHAR(255) NOT NULL,
    base_branch             VARCHAR(255) NOT NULL DEFAULT 'main',
    title                   VARCHAR(500) NOT NULL,
    description             TEXT,
    source                  VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    status                  VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    commit_sha              VARCHAR(40),
    commits                 JSONB,
    changed_files           JSONB,
    risk_level              VARCHAR(20),
    risk_report             JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_change_threads_user_id ON change_threads (user_id);
CREATE INDEX idx_change_threads_status ON change_threads (status);
