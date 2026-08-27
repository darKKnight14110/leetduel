package com.leetduel.matchmaking.event;

import java.util.UUID;

// Published to the durable matchmaking.join queue by QueueService at
// /queue/join time, consumed by JoinRequestListener - the only writer of
// the Redis pool/wait-start structures. See docs/goals.md's matchmaking
// design for why join acceptance and pool insertion are decoupled this way.
public record JoinRequestedEvent(UUID userId, int elo, long requestedAtEpochMillis) {
}
