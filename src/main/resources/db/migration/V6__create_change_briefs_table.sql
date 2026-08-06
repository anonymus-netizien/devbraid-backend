CREATE TABLE change_briefs
(
    id           UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    thread_id    UUID        NOT NULL UNIQUE REFERENCES change_threads (id) ON DELETE CASCADE,
    content      TEXT        NOT NULL,
    published_at TIMESTAMPTZ,
    publish_url  VARCHAR(500),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
