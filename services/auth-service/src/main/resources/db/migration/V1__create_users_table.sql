-- auth schema is owned exclusively by auth-service. No other service reads
-- or writes here directly (database-per-service) - User/Profile Service gets
-- its own schema/table and stores user_id as a plain UUID copied from here,
-- not a cross-schema foreign key.
CREATE SCHEMA IF NOT EXISTS auth;

CREATE TABLE auth.users (
    -- UUID, not a serial int: avoids sequential-id enumeration of accounts,
    -- and gives an identifier User/Profile Service can reuse without ever
    -- calling back into auth-service's database to look one up.
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Login handle. Uniqueness enforced by the DB, not just app-level check,
    -- so two concurrent signups with the same name can't both succeed
    -- (closes the TOCTOU race an app-only check leaves open).
    username VARCHAR(30) NOT NULL,

    -- bcrypt hash, never plaintext. TEXT rather than a fixed VARCHAR(60):
    -- bcrypt output is a fixed 60 chars today, but pinning the column to that
    -- length locks in bcrypt forever - a future move to Argon2 (longer hash)
    -- would need a migration just to widen the column.
    password_hash TEXT NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_username UNIQUE (username)
);
