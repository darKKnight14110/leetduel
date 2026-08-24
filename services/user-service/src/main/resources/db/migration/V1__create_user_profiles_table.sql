-- profile schema owned exclusively by user-service (database-per-service).
CREATE SCHEMA IF NOT EXISTS profile;

CREATE TABLE profile.user_profiles (
    -- Same UUID as auth.users.id, copied in at signup time - not a foreign
    -- key. A real FK would create a hard, disk-level coupling between two
    -- services that should only ever talk over the network; if auth-service
    -- (or its DB) is unreachable, profile reads/writes must still work.
    -- This is a 1:1 extension table, so user_id is the primary key directly
    -- rather than adding a redundant surrogate id.
    user_id UUID PRIMARY KEY,

    elo INTEGER NOT NULL DEFAULT 1200,

    -- External ratings are nullable: not every user links Codeforces/LeetCode.
    codeforces_rating INTEGER,
    leetcode_rating INTEGER,
    leetcode_total_solved INTEGER,

    duels_won INTEGER NOT NULL DEFAULT 0,
    duels_lost INTEGER NOT NULL DEFAULT 0,
    duels_drawn INTEGER NOT NULL DEFAULT 0,

    -- Running sums, not stored averages. avg_opp_elo_* = sum / count at read
    -- time. An average can't be folded into on a new result without also
    -- knowing the prior count (new_avg = (old_avg*old_count + x)/(old_count+1)),
    -- so storing sum+count directly turns every update into a plain atomic
    -- increment instead of a read-modify-write of a derived value.
    -- BIGINT, not INTEGER: elo (~thousands) * duel count over an account's
    -- lifetime can exceed INTEGER's ~2.1B ceiling; BIGINT removes the
    -- question entirely for negligible storage cost.
    sum_opp_elo_won BIGINT NOT NULL DEFAULT 0,
    sum_opp_elo_lost BIGINT NOT NULL DEFAULT 0,
    sum_opp_elo_drawn BIGINT NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
