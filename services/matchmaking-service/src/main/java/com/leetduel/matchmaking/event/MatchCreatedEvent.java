package com.leetduel.matchmaking.event;

import java.util.UUID;

// Published via the outbox to the match.events topic exchange, routing key
// match.created. Duel Service (Phase 3), WS Gateway, and Leaderboard
// Service each independently bind their own queue to this exchange and
// treat this event as the sole source of truth that a match exists - none
// of them ever read matchmaking-service's Postgres schema directly.
public record MatchCreatedEvent(
        UUID matchId,
        UUID userAId,
        UUID userBId,
        int userAEloAtMatch,
        int userBEloAtMatch,
        UUID problemId,
        int timeLimitMs) {
}
