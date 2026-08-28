package com.leetduel.problem.dto;

import com.leetduel.problem.problem.Difficulty;

import java.util.List;
import java.util.UUID;

// Internal indexing projection. It contains problem metadata and tags, but
// never test cases or reference solutions, so the practice service can build
// recommendations without becoming a second problem database.
public record ProblemCatalogDto(
        UUID problemId,
        String slug,
        String title,
        String description,
        Difficulty difficulty,
        List<String> tags
) {
}
