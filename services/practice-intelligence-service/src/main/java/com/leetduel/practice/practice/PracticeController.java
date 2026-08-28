package com.leetduel.practice.practice;

import com.leetduel.practice.dto.ExplanationResponse;
import com.leetduel.practice.dto.PracticeOverviewResponse;
import com.leetduel.practice.dto.ProblemProgressResponse;
import com.leetduel.practice.dto.RecommendationResponse;
import com.leetduel.practice.repository.PracticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/practice")
@RequiredArgsConstructor
public class PracticeController {

    private final PracticeRepository practiceRepository;
    private final RecommendationService recommendationService;
    private final ExplanationService explanationService;

    @GetMapping("/overview")
    public ResponseEntity<PracticeOverviewResponse> overview(@RequestHeader("X-User-Id") UUID userId) {
        PracticeRepository.ProgressCounts counts = practiceRepository.getProgressCounts(userId);
        return ResponseEntity.ok(new PracticeOverviewResponse(counts.attemptedCount(), counts.solvedCount(),
                recommendationService.getRecommendations(userId), practiceRepository.findProblemIds(userId, true),
                practiceRepository.findProblemIds(userId, false)));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<List<RecommendationResponse>> recommendations(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(recommendationService.getRecommendations(userId));
    }

    @GetMapping("/problems/{problemId}/progress")
    public ResponseEntity<ProblemProgressResponse> problemProgress(@RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID problemId) {
        return ResponseEntity.ok(practiceRepository.getProblemProgress(userId, problemId));
    }

    @GetMapping("/explanations/{submissionId}")
    public ResponseEntity<ExplanationResponse> explanation(@RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID submissionId) {
        ExplanationResponse response = practiceRepository.getExplanation(submissionId, userId);
        return response == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(response);
    }

    @PostMapping("/explanations/{submissionId}/retry")
    public ResponseEntity<ExplanationResponse> retryHint(@RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID submissionId) {
        ExplanationResponse current = practiceRepository.getExplanation(submissionId, userId);
        if (current == null) {
            return ResponseEntity.notFound().build();
        }
        practiceRepository.markHintRetryable(submissionId);
        explanationService.generateHint(submissionId);
        return ResponseEntity.accepted().body(practiceRepository.getExplanation(submissionId, userId));
    }

    @PostMapping("/explanations/{submissionId}/walkthrough")
    public ResponseEntity<ExplanationResponse> walkthrough(@RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID submissionId) {
        ExplanationResponse current = practiceRepository.getExplanation(submissionId, userId);
        if (current == null) {
            return ResponseEntity.notFound().build();
        }
        if (practiceRepository.claimWalkthrough(submissionId, userId)) {
            explanationService.generateWalkthrough(submissionId);
        }
        return ResponseEntity.accepted().body(practiceRepository.getExplanation(submissionId, userId));
    }
}
