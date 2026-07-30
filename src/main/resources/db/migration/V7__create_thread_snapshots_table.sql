-- V7: Create thread_snapshots table
-- Immutable snapshots of a ChangeThread's state at a point in time.
-- Used for reproducibility — "this is exactly what I reviewed".

CREATE TABLE thread_snapshots
(
    id                   UUID PRIMARY KEY      DEFAULT uuidv7(),
    thread_id            UUID         NOT NULL REFERENCES change_threads (id) ON DELETE CASCADE,
    user_id              UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    repository_full_name VARCHAR(500) NOT NULL,
    head_branch          VARCHAR(255) NOT NULL,
    base_branch          VARCHAR(255) NOT NULL,
    commit_sha           VARCHAR(40),
    commits              JSONB,
    changed_files        JSONB,
    title                VARCHAR(500) NOT NULL,
    description          TEXT,
    type                 VARCHAR(20)  NOT NULL DEFAULT 'CREATION',
    note                 TEXT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_snapshots_thread_id ON thread_snapshots (thread_id);
CREATE INDEX idx_snapshots_user_id ON thread_snapshots (user_id);
CREATE INDEX idx_snapshots_thread_type ON thread_snapshots (thread_id, type);
