package com.leetduel.practice.dto;

import java.util.List;
import java.util.UUID;

public record PracticeOverviewResponse(
        int attemptedCount,
        int solvedCount,
        List<RecommendationResponse> recommendations,
        List<UUID> solvedProblemIds,
        List<UUID> attemptedProblemIds
) {
}
