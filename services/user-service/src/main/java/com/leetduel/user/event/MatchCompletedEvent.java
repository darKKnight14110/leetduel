package com.leetduel.user.event;

import java.util.UUID;

// Consumer-side duplicate of duel-service's producer record of the same
// name - independently maintained, matching this repo's no-shared-lib
// convention.
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
