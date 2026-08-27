-- duel schema owned exclusively by duel-service (database-per-service).
-- This is a SEPARATE table from matchmaking.matches - this service never
-- reads matchmaking-service's DB directly, only its match.created event.
-- Same id (matchId) as matchmaking.matches.id, carried over from that event.
CREATE SCHEMA IF NOT EXISTS duel;

CREATE TABLE duel.matches (
    -- NOT gen_random_uuid() default - this id IS matchmaking-service's
    -- match.created event's matchId, inserted explicitly by
    -- MatchCreatedListener, never generated here.
    id UUID PRIMARY KEY,

    player1_id UUID NOT NULL,
    player2_id UUID NOT NULL,

    -- ELO-at-match-time, frozen from the match.created payload - same
    -- reasoning as matchmaking.matches's identical columns. Used for the
    -- ELO delta calculation on completion, never re-read live.
    player1_elo_at_match INTEGER NOT NULL,
    player2_elo_at_match INTEGER NOT NULL,

    problem_id UUID NOT NULL,
    time_limit_ms INTEGER NOT NULL,

    -- Best-score-to-date per player, 0-100. Monotonically non-decreasing -
    -- see MatchService's max(existing, new) update rule.
    player1_progress_pct INTEGER NOT NULL DEFAULT 0,
    player2_progress_pct INTEGER NOT NULL DEFAULT 0,

    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    winner_id UUID,
    is_draw BOOLEAN NOT NULL DEFAULT false,
    player1_elo_delta INTEGER,
    player2_elo_delta INTEGER,

    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at TIMESTAMPTZ,

    -- JPA @Version optimistic-lock column - guards the IN_PROGRESS ->
    -- COMPLETED transition against two near-simultaneous writers (see
    -- Match.java's comment on the field).
    version BIGINT NOT NULL DEFAULT 0
);

-- Serves the timeout sweep's "find IN_PROGRESS matches past deadline" scan -
-- partial index since most rows are terminal shortly after they're created.
CREATE INDEX idx_matches_in_progress ON duel.matches (started_at)
    WHERE status = 'IN_PROGRESS';
