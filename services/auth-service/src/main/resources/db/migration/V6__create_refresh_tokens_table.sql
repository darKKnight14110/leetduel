-- Refresh tokens live in Postgres, not just as a longer-lived JWT, because a
-- JWT can't be revoked before it expires (stateless-verify is the whole
-- point of it) - password reset, logout, and stolen-token detection all need
-- a row here to flip to "no longer valid" mid-lifetime.
CREATE TABLE auth.refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,

    -- Same SHA-256-of-random-opaque-value reasoning as auth.tokens - this
    -- one is a long-lived bearer credential sitting in a client's storage,
    -- so a DB dump alone must not yield usable refresh tokens.
    token_hash VARCHAR(64) NOT NULL,

    expires_at TIMESTAMPTZ NOT NULL,

    -- NULL = active. Set on rotation (old token revoked the moment its
    -- replacement is issued) and on logout/password-reset (session killed
    -- outright). Rotation is also the reuse-detection hook: if a revoked
    -- token is presented again, that's a token that got copied somewhere it
    -- shouldn't have been - RefreshTokenService treats that as a theft
    -- signal and revokes the whole user's session set, not just this token.
    revoked_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);

-- Serves both the reuse-detection revoke-all sweep and a future
-- "log out of all devices" feature - partial on active rows only, since a
-- user's revoked history is never queried by either path.
CREATE INDEX idx_refresh_tokens_user_active
    ON auth.refresh_tokens (user_id)
    WHERE revoked_at IS NULL;
