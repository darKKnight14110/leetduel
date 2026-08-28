package com.leetduel.practice.dto;

import java.time.Instant;
import java.util.UUID;

public record ProblemProgressResponse(
        UUID problemId,
        int attemptedCount,
        boolean solved,
        String lastVerdict,
        Instant lastAttemptAt
) {
}
