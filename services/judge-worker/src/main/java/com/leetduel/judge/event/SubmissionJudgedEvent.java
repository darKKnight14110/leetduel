package com.leetduel.judge.event;

import java.util.List;
import java.util.UUID;

// Producer-side duplicate of submission-service's consumer record. Carries
// the COMPLETE result - this service is stateless and persists nothing
// itself, so this event IS the only record of a judged run until
// submission-service's listener writes it into submissions.test_results.
public record SubmissionJudgedEvent(
        UUID submissionId,
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
