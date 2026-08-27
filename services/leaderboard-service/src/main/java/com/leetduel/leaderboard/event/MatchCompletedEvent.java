package com.leetduel.leaderboard.event;

import java.util.UUID;

// Consumer-side independent copy of duel-service's producer record of the
// same name - matches this repo's no-shared-lib convention (see
// user-service's own copy of this same event for precedent).
public record MatchCompletedEvent(
        UUID matchId,
        UUID player1Id,
        UUID player2Id,
        UUID winnerId,
        boolean isDraw,
        int player1EloAtMatch,
        int player2EloAtMatch,
        int player1EloDelta,
        int player2EloDelta) {
}
