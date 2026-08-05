-- V19: Durable webhook job queue
-- Rows are claimed by WebhookJobWorker via SELECT ... FOR UPDATE SKIP LOCKED,
-- retried with exponential backoff, and dead-lettered as FAILED after max attempts.

CREATE TABLE webhook_jobs
(
    id              UUID PRIMARY KEY      DEFAULT uuidv7(),
    webhook_id      UUID         NOT NULL REFERENCES github_webhooks (id),
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB        NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL,
    last_error      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_webhook_jobs_status_next_attempt ON webhook_jobs (status, next_attempt_at);
CREATE INDEX idx_webhook_jobs_webhook_id ON webhook_jobs (webhook_id);
