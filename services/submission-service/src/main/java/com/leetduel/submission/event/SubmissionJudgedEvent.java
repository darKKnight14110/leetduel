package com.leetduel.submission.event;

import java.util.List;
import java.util.UUID;

// Message body consumed from judge.events (topic exchange), routing key
// submission.judged. Carries the COMPLETE result - Judge Worker is
// stateless and persists nothing itself, so this event IS the only record
// of a judged run until this listener writes it into submissions.test_results.
public record SubmissionJudgedEvent(
        UUID submissionId,
        // Mirrors JudgeJobCreatedEvent.matchId - not used by this listener
        // (submission-service updates by submissionId, not matchId), kept
        // only so this DTO deserializes the full payload Judge Worker sends.
        UUID matchId,
        UUID userId,
        String verdict,
        int testCasesPassed,
        int testCasesTotal,
        List<TestCaseResultPayload> testResults
) {

    public record TestCaseResultPayload(
            int ordinal,
            String status,
            Long runtimeMs,
            String expectedOutput,
            String actualOutput
    ) {
    }
}
