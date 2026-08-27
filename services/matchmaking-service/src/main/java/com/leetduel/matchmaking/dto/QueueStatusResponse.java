package com.leetduel.matchmaking.dto;

import java.util.UUID;

public record QueueStatusResponse(QueueState state, UUID matchId) {
}
