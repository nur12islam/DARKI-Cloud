-- DARKI Cloud — Telegram OIDC login attempts and one-time app exchange codes

CREATE TABLE IF NOT EXISTS telegram_login_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    state_hash CHAR(64) NOT NULL UNIQUE,
    code_verifier TEXT NOT NULL,
    exchange_code_hash CHAR(64) UNIQUE,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT telegram_login_attempts_expiry_valid CHECK (expires_at > created_at)
);

CREATE INDEX IF NOT EXISTS telegram_login_attempts_expiry_idx
    ON telegram_login_attempts (expires_at);

CREATE INDEX IF NOT EXISTS telegram_login_attempts_exchange_idx
    ON telegram_login_attempts (exchange_code_hash)
    WHERE exchange_code_hash IS NOT NULL AND used_at IS NULL;
