package com.leetduel.problem.dto;

import com.leetduel.problem.problem.Difficulty;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Public detail response - sampleTestCases only ever contains is_sample=true
// rows. See ProblemService.getPublicDetail for the filter this depends on.
public record ProblemDetailDto(
        UUID id,
        String slug,
        String title,
        String description,
        Difficulty difficulty,
        String functionName,
        String returnType,
        List<ParameterDto> parameters,
        Map<String, String> languageStubs,
        List<TestCaseDto> sampleTestCases
) {
}
