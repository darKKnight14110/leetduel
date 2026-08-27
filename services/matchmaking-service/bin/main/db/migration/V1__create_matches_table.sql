-- matchmaking schema owned exclusively by matchmaking-service
-- (database-per-service). This is the durable record that a match was
-- made - NOT a Duel Service table. Duel Service (Phase 3) owns its own
-- lifecycle table and treats match.created as the source of truth, never
-- reading this DB directly - see docs/goals.md's "who owns what" note.
CREATE SCHEMA IF NOT EXISTS matchmaking;

CREATE TABLE matchmaking.matches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Copied at match time, not FKs - same database-per-service reasoning
    -- as submission.submissions.user_id/problem_id.
    user_a_id UUID NOT NULL,
    user_b_id UUID NOT NULL,

    -- ELO-at-match-time, not a live lookup - a later match.completed
    -- consumer needs exactly this frozen value (see goals.md's "opponent's
    -- ELO-at-match-time, not live ELO" note on the duel flow).
    user_a_elo_at_match INTEGER NOT NULL,
    user_b_elo_at_match INTEGER NOT NULL,

    problem_id UUID NOT NULL,
    time_limit_ms INTEGER NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_matches_user_a ON matchmaking.matches (user_a_id);
CREATE INDEX idx_matches_user_b ON matchmaking.matches (user_b_id);
