package com.leetduel.practice.practice;

import com.leetduel.practice.ai.EmbeddingService;
import com.leetduel.practice.dto.RecommendationResponse;
import com.leetduel.practice.repository.PracticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final PracticeRepository practiceRepository;
    private final EmbeddingService embeddingService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${leetduel.practice.recommendation-cache-seconds}")
    private long cacheSeconds;

    @Value("${leetduel.practice.max-recommendations}")
    private int maxRecommendations;

    @Value("${leetduel.practice.max-candidates}")
    private int maxCandidates;

    public List<RecommendationResponse> getRecommendations(UUID userId) {
        String key = "practice:recommendations:" + userId;
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                RecommendationResponse[] values = objectMapper.readValue(cached, RecommendationResponse[].class);
                return List.of(values);
            }
        } catch (Exception ignored) {
        }

        List<String> weakTags = practiceRepository.getWeakTags(userId);
        String queryVector = null;
        if (!weakTags.isEmpty()) {
            try {
                queryVector = embeddingService.queryVector("Weak topics: " + String.join(", ", weakTags));
            } catch (RuntimeException ignored) {
            }
        }
        List<PracticeRepository.RecommendationCandidate> candidates = practiceRepository.findCandidates(userId, queryVector,
                Math.max(maxCandidates, maxRecommendations));
        if (candidates.isEmpty() && queryVector != null) {
            candidates = practiceRepository.findCandidates(userId, null, Math.max(maxCandidates, maxRecommendations));
        }
        List<RecommendationResponse> recommendations = candidates.stream()
                .map(candidate -> scored(candidate, weakTags))
                .sorted(Comparator.comparingDouble(Scored::score).reversed()
                        .thenComparing(scored -> scored.candidate().problemId()))
                .limit(maxRecommendations)
                .map(scored -> RecommendationResponse.from(scored.candidate(), scored.reason(), scored.score()))
                .toList();
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(recommendations), Duration.ofSeconds(cacheSeconds));
        } catch (Exception ignored) {
        }
        return recommendations;
    }

    public void invalidate(UUID userId) {
        try {
            redisTemplate.delete("practice:recommendations:" + userId);
        } catch (RuntimeException ignored) {
        }
    }

    private Scored scored(PracticeRepository.RecommendationCandidate candidate, List<String> weakTags) {
        long matchingTags = candidate.tags().stream().filter(weakTags::contains).count();
        double weaknessOverlap = weakTags.isEmpty() ? 0.0 : (double) matchingTags / weakTags.size();
        double difficultyFit = switch (candidate.difficulty()) {
            case "EASY" -> 1.0;
            case "MEDIUM" -> weakTags.isEmpty() ? 0.5 : 0.85;
            case "HARD" -> weakTags.size() >= 3 ? 0.65 : 0.3;
            default -> 0.4;
        };
        double novelty = 1.0 / (1.0 + candidate.attempts());
        double semantic = Math.max(0.0, Math.min(1.0, candidate.semanticSimilarity()));
        double score = 0.50 * semantic + 0.30 * weaknessOverlap + 0.15 * difficultyFit + 0.05 * novelty;
        String reason = matchingTags > 0
                ? "Practice your weak topic: " + String.join(", ", candidate.tags().stream().filter(weakTags::contains).limit(2).toList())
                : candidate.attempts() == 0 ? "A fresh challenge at a steady pace" : "A nearby problem to reinforce the pattern";
        return new Scored(candidate, reason, score);
    }

    private record Scored(PracticeRepository.RecommendationCandidate candidate, String reason, double score) {
    }
}
