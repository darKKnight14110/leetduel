-- Idempotency guard for the match.completed consumer. Unlike
-- UserCreatedListener (naturally idempotent - existsById on the profile
-- row IS the dedup check), applying an ELO delta is a pure read-modify-write
-- with no natural unique constraint to lean on: redelivery of the same
-- match.completed event would double-count the win/loss. matchId is the
-- dedup key, not a synthetic event id - Duel Service's optimistic-lock CAS
-- guarantees match.completed is only ever PUBLISHED once per match, so
-- "have we processed this matchId's completion" is exactly the question
-- that needs answering.
CREATE TABLE profile.processed_match_completions (
    match_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
