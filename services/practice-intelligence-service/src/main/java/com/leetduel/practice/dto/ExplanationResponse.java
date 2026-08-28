package com.leetduel.practice.dto;

import java.time.Instant;
import java.util.UUID;

public record ExplanationResponse(
        UUID submissionId,
        String hintStatus,
        ExplanationContent hint,
        String walkthroughStatus,
        ExplanationContent walkthrough,
        int retryCount,
        String lastError,
        Instant updatedAt
) {
}
