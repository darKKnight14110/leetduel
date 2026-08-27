package com.leetduel.leaderboard.config;

import java.util.UUID;

// Key-naming is the entire rollover/idempotency story for this service -
// see PeriodKeyResolver and increment_period_score.lua for the mechanics
// these keys plug into.
public final class LeaderboardKeys {

    public static final String GLOBAL = "leaderboard:global";

    public static String weekly(String isoWeek) {
        return "leaderboard:weekly:" + isoWeek;
    }

    public static String season(String quarter) {
        return "leaderboard:season:" + quarter;
    }

    // One marker per (period, matchId, userId) - NOT per matchId alone,
    // since a single match.completed event applies a (different) delta to
    // BOTH players, and each player's application must be deduped
    // independently.
    public static String appliedMarker(String period, UUID matchId, UUID userId) {
        return "leaderboard:applied:" + period + ":" + matchId + ":" + userId;
    }

    private LeaderboardKeys() {
    }
}
