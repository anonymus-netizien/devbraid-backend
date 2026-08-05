CREATE TABLE pr_reviews
(
    id                UUID PRIMARY KEY     DEFAULT uuidv7(),
    thread_id         UUID        NOT NULL REFERENCES change_threads (id) ON DELETE CASCADE,
    pr_number         INT         NOT NULL,
    head_sha          VARCHAR(40) NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    summary           TEXT,
    severity_counts   JSONB,
    error             TEXT,
    published         BOOLEAN     NOT NULL DEFAULT FALSE,
    github_review_id  BIGINT,
    github_review_url VARCHAR(500),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at      TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_pr_reviews_thread_head_sha ON pr_reviews (thread_id, head_sha);
CREATE INDEX idx_pr_reviews_thread_id ON pr_reviews (thread_id);

CREATE TABLE pr_review_comments
(
    id                UUID PRIMARY KEY       DEFAULT uuidv7(),
    review_id         UUID          NOT NULL REFERENCES pr_reviews (id) ON DELETE CASCADE,
    file_path         VARCHAR(1000) NOT NULL,
    line_number       INT,
    severity          VARCHAR(20)   NOT NULL,
    category          VARCHAR(30)   NOT NULL,
    title             VARCHAR(500)  NOT NULL,
    body              TEXT          NOT NULL,
    github_comment_id BIGINT,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pr_review_comments_review_id ON pr_review_comments (review_id);
