-- Google Sign-In removed from this service - see V5 migration for the
-- table's original reasoning. Dropped via a new forward migration rather
-- than editing/deleting V5: Flyway validates already-applied migrations by
-- checksum, so rewriting history under it would break any DB that already
-- ran V5, rather than cleanly evolving forward from wherever it's at.
DROP TABLE auth.oauth_identities;
