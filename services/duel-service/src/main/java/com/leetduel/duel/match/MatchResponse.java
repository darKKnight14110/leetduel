package com.leetduel.duel.match;

import java.time.Instant;
import java.util.UUID;

// Reads entirely from this service's own row - the frontend's
// initial-load / reconnect-recovery path (see docs/goals.md's Phase 3
// plan). Deliberately IDs only, not usernames/display-ELO - resolving those
// is User/Profile Service's job (GET /internal/profiles/{userId}), not
// duplicated here across a database-per-service boundary.
public record MatchResponse(
        UUID matchId,
        UUID player1Id,
        UUID player2Id,
        UUID problemId,
        int timeLimitMs,
        int player1ProgressPct,
        int player2ProgressPct,
        MatchStatus status,
        UUID winnerId,
        boolean isDraw,
        Instant startedAt) {

    public static MatchResponse from(Match m) {
        return new MatchResponse(m.getId(), m.getPlayer1Id(), m.getPlayer2Id(), m.getProblemId(), m.getTimeLimitMs(),
                m.getPlayer1ProgressPct(), m.getPlayer2ProgressPct(), m.getStatus(), m.getWinnerId(), m.isDraw(),
                m.getStartedAt());
    }
}
