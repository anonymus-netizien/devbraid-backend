-- V9: Create thread_events table
-- Immutable timeline of all actions taken on a thread.
-- Provides a chronological view of the thread's evolution.

CREATE TABLE thread_events
(
    id         UUID PRIMARY KEY      DEFAULT uuidv7(),
    thread_id  UUID         NOT NULL REFERENCES change_threads (id) ON DELETE CASCADE,
    actor_id   UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(30)  NOT NULL,
    summary    VARCHAR(500) NOT NULL,
    metadata   JSONB,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_thread_events_thread_id ON thread_events (thread_id);
CREATE INDEX idx_thread_events_thread_created ON thread_events (thread_id, created_at DESC);
CREATE INDEX idx_thread_events_actor_id ON thread_events (actor_id);
