package com.leetduel.problem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// The source identity is separate from the user-facing slug. Slugs can be
// edited, while source+sourceId makes retries of a dataset import idempotent.
public record ProblemImportRequest(
        @NotBlank String source,
        @NotBlank String sourceId,
        @NotNull @Valid CreateProblemRequest problem
) {
}
