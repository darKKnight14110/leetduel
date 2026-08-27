package com.leetduel.duel.event;

import java.util.UUID;

// Consumer-side duplicate of matchmaking-service's producer record of the
// same name - independently maintained, matching this repo's no-shared-lib
// convention (the wire contract is the interface, not a shared class).
public record MatchCreatedEvent(
        UUID matchId,
        UUID userAId,
        UUID userBId,
        int userAEloAtMatch,
        int userBEloAtMatch,
        UUID problemId,
        int timeLimitMs) {
}
