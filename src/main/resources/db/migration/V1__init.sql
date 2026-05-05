CREATE TABLE chat_sessions
(
    id         UUID PRIMARY KEY,
    title      VARCHAR(120)                NOT NULL,
    created_at TIMESTAMPTZ                 NOT NULL,
    updated_at TIMESTAMPTZ                 NOT NULL
);

CREATE TABLE chat_messages
(
    id           UUID PRIMARY KEY,
    chat_id      UUID                        NOT NULL REFERENCES chat_sessions (id) ON DELETE CASCADE,
    role         VARCHAR(20)                 NOT NULL,
    content      TEXT                        NOT NULL,
    model        VARCHAR(120),
    duration_ms  BIGINT,
    total_tokens INTEGER,
    created_at   TIMESTAMPTZ                 NOT NULL
);

CREATE INDEX idx_chat_messages_chat_id_created_at
    ON chat_messages (chat_id, created_at);

CREATE INDEX idx_chat_sessions_updated_at
    ON chat_sessions (updated_at DESC);
