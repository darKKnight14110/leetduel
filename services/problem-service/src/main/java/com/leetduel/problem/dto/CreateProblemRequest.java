package com.leetduel.problem.dto;

import com.leetduel.problem.problem.Difficulty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record CreateProblemRequest(
        @NotBlank String slug,
        @NotBlank String title,
        @NotBlank String description,
        @NotNull Difficulty difficulty,
        Integer timeLimitMs,
        Integer memoryLimitMb,
        List<String> tags,
        @NotBlank String functionName,
        @NotBlank String returnType,
        @NotEmpty @Valid List<ParameterDto> parameters,
        // language -> stub code, e.g. {"PYTHON": "...", "JAVA": "..."}
        @NotEmpty Map<String, String> languageStubs,
        @NotEmpty @Valid List<TestCaseRequest> testCases
) {
    public record TestCaseRequest(@NotBlank String input, @NotBlank String expectedOutput, boolean isSample) {
    }
}
