package com.leetduel.matchmaking.dto;

import java.util.UUID;

// Mirrors problem-service's new InternalRandomProblemDto response shape -
// see InternalProblemController#getRandom.
public record InternalRandomProblemResponse(UUID problemId) {
}
