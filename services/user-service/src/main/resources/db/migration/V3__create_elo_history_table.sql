-- Append-only rating history feeding the profile page's rating chart
-- (Phase 4). Populated inside the SAME transaction as the ELO write in
-- UserProfileService.applyMatchResult, so this table is exactly as
-- consistent as the profile.elo column it's derived from - no separate
-- consistency story needed.
CREATE TABLE profile.elo_history (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id UUID NOT NULL,
    match_id UUID NOT NULL,
    elo_after INTEGER NOT NULL,
    elo_delta INTEGER NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Serves "give me this user's rating history in order" - the only read
-- pattern this table has.
CREATE INDEX idx_elo_history_user_recorded_at ON profile.elo_history (user_id, recorded_at);

-- Belt-and-suspenders alongside profile.processed_match_completions: turns
-- any accidental double-insert (e.g. a future code path that forgets the
-- existing dedup check) into a rejected duplicate at the DB level, not a
-- silently doubled history row.
CREATE UNIQUE INDEX uq_elo_history_user_match ON profile.elo_history (user_id, match_id);
