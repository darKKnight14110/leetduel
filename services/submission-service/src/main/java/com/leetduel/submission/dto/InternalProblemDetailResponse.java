package com.leetduel.submission.dto;

import java.util.List;
import java.util.UUID;

// Local copy of problem-service's InternalProblemDetailDto response shape -
// deliberately duplicated, not shared-lib'd, matching this repo's
// convention that the wire contract (JSON field names) is the actual
// interface between independently deployable services, not a shared class.
public record InternalProblemDetailResponse(
        UUID problemId,
        String functionName,
        String returnType,
        List<ParameterResponse> parameters,
        int timeLimitMs,
        int memoryLimitMb,
        List<TestCaseResponse> testCases
) {

    public record ParameterResponse(String name, String type) {
    }

    public record TestCaseResponse(int ordinal, String input, String expectedOutput, boolean sample) {

        public TestCaseResponse(int ordinal, String input, String expectedOutput) {
            this(ordinal, input, expectedOutput, false);
        }
    }
}
