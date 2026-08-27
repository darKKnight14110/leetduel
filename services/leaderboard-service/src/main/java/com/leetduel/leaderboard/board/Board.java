package com.leetduel.leaderboard.board;

// GLOBAL ranks by absolute all-time ELO (ZADD, naturally idempotent).
// WEEKLY/SEASON rank by cumulative ELO gained within the current
// ISO-week/calendar-quarter (ZINCRBY, needs the idempotency-guarded Lua
// path) - see docs/goals.md's Phase 4 plan for why these are a genuinely
// different competitive framing (improvement, not standing) and a
// different Redis access pattern, not just the same board sliced by time.
public enum Board {
    GLOBAL,
    WEEKLY,
    SEASON
}
