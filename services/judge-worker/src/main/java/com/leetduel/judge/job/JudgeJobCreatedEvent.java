package com.leetduel.judge.job;

import java.util.List;
import java.util.UUID;

// Consumer-side duplicate of submission-service's record of the same name -
// independently maintained, matching this repo's no-shared-lib convention.
// The wire contract (JSON field names) is the actual interface between the
// two services, not a shared class.
public record JudgeJobCreatedEvent(
        UUID submissionId,
        UUID problemId,
        UUID userId,
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
