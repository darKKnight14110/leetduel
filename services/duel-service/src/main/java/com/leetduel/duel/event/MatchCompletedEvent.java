package com.leetduel.duel.event;

import java.util.UUID;

// Published via the outbox to match.events, routing key match.completed.
// User Service (Phase 3) is the sole writer of ELO - it applies
// player1EloDelta/player2EloDelta and the duel W/L/D counters using
// player{1,2}EloAtMatch (frozen, NOT either player's post-match live ELO -
// see docs/goals.md's "opponent's ELO-at-match-time" note on why). WS
// Gateway also consumes this to push the final result over WS. Leaderboard
// Service (Phase 4) will bind its own queue to the same routing key.
public record MatchCompletedEvent(
        UUID matchId,
        UUID player1Id,
        UUID player2Id,
        // Null when isDraw is true.
        UUID winnerId,
        boolean isDraw,
        int player1EloAtMatch,
        int player2EloAtMatch,
        int player1EloDelta,
        int player2EloDelta) {
}
