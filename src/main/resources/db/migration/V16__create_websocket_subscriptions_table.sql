-- V16: Create websocket_subscriptions table for tracking active STOMP subscriptions
-- Used for observability and (future) targeted fan-out per user.

CREATE TABLE websocket_subscriptions
(
    id            UUID PRIMARY KEY       DEFAULT gen_random_uuid(),
    user_id       UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    session_id    VARCHAR(128)  NOT NULL,
    destination   VARCHAR(255)  NOT NULL,
    subscribed_at TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ws_sub_user ON websocket_subscriptions (user_id);
CREATE INDEX idx_ws_sub_session ON websocket_subscriptions (session_id);
