package com.leetduel.practice.dto;

import com.leetduel.practice.repository.PracticeRepository;

import java.util.List;
import java.util.UUID;

public record RecommendationResponse(
        UUID problemId,
        String slug,
        String title,
        String difficulty,
        List<String> tags,
        String reason,
        double score
) {

    public static RecommendationResponse from(PracticeRepository.RecommendationCandidate candidate, String reason,
            double score) {
        return new RecommendationResponse(candidate.problemId(), candidate.slug(), candidate.title(),
                candidate.difficulty(), candidate.tags(), reason, score);
    }
}
