-- V8: Create file_comments table
-- Inline comments on specific files within a ChangeThread.
-- Allows developers to annotate specific files with reasoning or notes.

CREATE TABLE file_comments
(
    id         UUID PRIMARY KEY       DEFAULT uuidv7(),
    thread_id  UUID          NOT NULL REFERENCES change_threads (id) ON DELETE CASCADE,
    author_id  UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    file_path  VARCHAR(1000) NOT NULL,
    line_start INTEGER,
    line_end   INTEGER,
    content    TEXT          NOT NULL,
    status     VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_file_comments_thread_id ON file_comments (thread_id);
CREATE INDEX idx_file_comments_thread_file ON file_comments (thread_id, file_path);
CREATE INDEX idx_file_comments_author_id ON file_comments (author_id);
