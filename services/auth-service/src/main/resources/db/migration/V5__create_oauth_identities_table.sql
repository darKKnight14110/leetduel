-- Separate linking table, not a "google_id" column bolted onto auth.users -
-- a user can hold a local password AND a linked Google identity at once
-- (link Google to an existing password account, or vice versa later), and a
-- single provider column can't express "zero or more providers per user"
-- without turning into a wide sparse row per future provider added.
CREATE TABLE auth.oauth_identities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,

    -- "GOOGLE" today; a plain column, not its own lookup table, since the
    -- provider set changes rarely enough that a migration to add a new one
    -- is the right cost, not a runtime-configurable list.
    provider VARCHAR(20) NOT NULL,

    -- Google's "sub" claim - stable, unique per Google account, and
    -- deliberately NOT the email: Google lets the email on an account change,
    -- sub never does, so keying on email here would let a future email
    -- change on the Google side silently detach the link.
    provider_user_id VARCHAR(255) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- The actual identity check at login time: "has this exact Google
    -- account already been linked to someone." One Google account can't
    -- attach to two LeetDuel users.
    CONSTRAINT uq_oauth_provider_identity UNIQUE (provider, provider_user_id)
);

-- Serves "does this user already have a Google identity linked" (shown on a
-- future account-settings page) without a full table scan.
CREATE INDEX idx_oauth_identities_user_id ON auth.oauth_identities (user_id);
