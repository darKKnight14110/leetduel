package com.leetduel.submission.dto;

import com.leetduel.submission.submission.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateSubmissionRequest(
        @NotNull UUID problemId,
        @NotNull Language language,
        @NotBlank String sourceCode,
        // Null for practice-mode submissions. Set by the live duel page when
        // submitting from a match - see docs/goals.md's Phase 3 duel flow.
        UUID matchId
) {
}
