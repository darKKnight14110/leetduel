-- Email becomes the account's real contact channel (verification, password
-- reset), username stays the login handle/display name. Two columns, two
-- jobs - collapsing them would mean a username change breaks email delivery
-- or vice versa.
ALTER TABLE auth.users ADD COLUMN email VARCHAR(255);

-- Backfill guard, not a real account: only fires if this migration runs
-- against a dev DB that already has rows from before email existed (a fresh
-- DB has none). Placeholder addresses are globally unique via the row's own
-- id, so the UNIQUE constraint below can never collide on them - but they're
-- not deliverable, so any such row needs its email fixed by hand before the
-- account can use verification or reset.
UPDATE auth.users SET email = id || '+legacy@leetduel.local' WHERE email IS NULL;

ALTER TABLE auth.users ALTER COLUMN email SET NOT NULL;

-- Lowercased at the application layer before every read/write (see User
-- entity setter) so "Foo@gmail.com" and "foo@gmail.com" can't register as
-- two accounts. The DB only enforces uniqueness of whatever string it's
-- given - it doesn't know about case-folding - so this constraint is only
-- as good as the app never skipping normalization on some code path.
ALTER TABLE auth.users ADD CONSTRAINT uq_users_email UNIQUE (email);

-- A Google-only signup never sets a local password, so this can no longer
-- be NOT NULL. "does this account have a usable password" becomes an
-- application-level check (password_hash IS NOT NULL), not a DB constraint -
-- a CHECK here can't also see whether a matching row exists in
-- oauth_identities, so it can't express the real invariant ("has a password
-- OR has at least one linked provider") on its own.
ALTER TABLE auth.users ALTER COLUMN password_hash DROP NOT NULL;

-- False until the verify-email link is clicked, or true immediately for a
-- Google signup (Google already verified that email). Read by JwtService
-- into the access token claims so downstream services don't need a DB round
-- trip just to gate a "verified users only" action.
ALTER TABLE auth.users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT false;
