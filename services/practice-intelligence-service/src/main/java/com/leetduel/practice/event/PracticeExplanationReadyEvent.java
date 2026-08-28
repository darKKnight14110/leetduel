package com.leetduel.practice.event;

import java.time.Instant;
import java.util.UUID;

public record PracticeExplanationReadyEvent(
        UUID submissionId,
        UUID userId,
        String status,
        Instant readyAt
) {
}
