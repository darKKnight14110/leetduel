package com.leetduel.submission.event;

import java.time.Instant;
import java.util.UUID;

// Practice-only event. It is intentionally separate from submission.judged:
// source code is private learning data and must not be delivered to Duel or
// WebSocket consumers that only need match progress.
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
