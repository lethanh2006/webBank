-- Idempotency module: one row per (scope, client key), written in the same transaction as the
-- business operation, so the stored response exists if and only if the operation committed.
CREATE TABLE idempotency_key (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scope         VARCHAR(100) NOT NULL,
    idem_key      VARCHAR(64)  NOT NULL,
    request_hash  CHAR(64)     NOT NULL,
    response_body JSONB,
    created_at    TIMESTAMPTZ  NOT NULL,
    expires_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_idempotency_scope_key UNIQUE (scope, idem_key)
);

CREATE INDEX ix_idempotency_expires_at ON idempotency_key (expires_at);
