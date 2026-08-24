-- Single table for both email-verification and password-reset tokens, not
-- two near-identical tables - same shape (opaque bearer credential, one
-- user, expires, single-use), same lifecycle. "type" is what tells them
-- apart at lookup time.
CREATE TABLE auth.tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,

    -- SHA-256 hex of the raw token, never the raw token itself - the raw
    -- value goes out in an email (effectively a bearer credential in transit
    -- and in the recipient's inbox forever), so a DB leak alone must not be
    -- enough to hand out a working reset/verify link. Unlike password_hash,
    -- this is SHA-256 (fast) not bcrypt (slow) - the input is a 256-bit
    -- random value, not a low-entropy human password, so brute force is
    -- infeasible regardless of hash speed, and a fast hash is what a
    -- verify/reset request needs since it does this lookup synchronously.
    token_hash VARCHAR(64) NOT NULL,

    type VARCHAR(30) NOT NULL,

    expires_at TIMESTAMPTZ NOT NULL,

    -- NULL = still redeemable. Set on first successful use so a captured
    -- link (email forwarded, browser history, etc) can't be replayed.
    used_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_tokens_token_hash UNIQUE (token_hash)
);

-- Serves "invalidate this user's other pending tokens of this type before
-- issuing a new one" (resend-verification, forgot-password) - partial on
-- unused rows only, since used/expired history is never queried by this
-- path and shouldn't bloat the index.
CREATE INDEX idx_tokens_user_type_unused
    ON auth.tokens (user_id, type)
    WHERE used_at IS NULL;
