package com.leetduel.submission.event;

import java.util.List;
import java.util.UUID;

// Message body published on judge.jobs.exchange, routing key
// judge.job.created. Carries a full point-in-time snapshot of the problem's
// signature + every test case (hidden included) - fetched once from
// Problem Service at submission-create time, not re-fetched by Judge
// Worker at consume time. See the Phase 1 plan for why: decouples judging's
// critical path from Problem Service's uptime.
public record JudgeJobCreatedEvent(
        UUID submissionId,
        UUID problemId,
        UUID userId,
        // Null for practice-mode submissions; pass-through only - Judge
        // Worker never reads this, it just carries it into
        // SubmissionJudgedEvent for Duel Service (Phase 3+).
        UUID matchId,
        String language,
        String sourceCode,
        String functionName,
        String returnType,
        List<ParameterPayload> parameters,
        int timeLimitMs,
        int memoryLimitMb,
        List<TestCasePayload> testCases
) {

    public record ParameterPayload(String name, String type) {
    }

    public record TestCasePayload(int ordinal, String input, String expectedOutput) {
    }
}
