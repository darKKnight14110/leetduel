package com.leetduel.problem.dto;

import java.util.List;
import java.util.UUID;

// Judge-facing payload - every test case (hidden included) plus the limits
// needed to run them. Never exposed through the Gateway; only reachable by
// a direct service-to-service call to this internal endpoint.
public record InternalProblemDetailDto(
        UUID problemId,
        String functionName,
        String returnType,
        List<ParameterDto> parameters,
        int timeLimitMs,
        int memoryLimitMb,
        List<TestCaseDto> testCases
) {
}
