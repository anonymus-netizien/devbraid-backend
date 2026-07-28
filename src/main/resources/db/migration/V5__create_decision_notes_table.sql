CREATE TABLE decision_notes
(
    id              UUID PRIMARY KEY DEFAULT uuidv7(),
    thread_id       UUID        NOT NULL REFERENCES change_threads (id) ON DELETE CASCADE,
    author_id       UUID        NOT NULL REFERENCES users (id),
    context         VARCHAR(20) NOT NULL,
    context_ref     VARCHAR(500),
    decision        TEXT        NOT NULL,
    rationale       TEXT        NOT NULL,
    alternatives    TEXT,
    impact          TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_decision_notes_thread_id ON decision_notes (thread_id);
CREATE INDEX idx_decision_notes_author_id ON decision_notes (author_id);
