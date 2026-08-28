package com.leetduel.practice.event;

import java.time.Instant;
import java.util.UUID;

// Independent consumer copy of the practice-only wire contract. The event
// contains sanitized diagnostics, never hidden expected/actual output.
public record PracticeSubmissionCompletedEvent(
        UUID submissionId,
        UUID userId,
        UUID problemId,
        String language,
        String sourceCode,
        String verdict,
        int testCasesPassed,
        int testCasesTotal,
        String diagnostics,
        Instant judgedAt
) {
}
