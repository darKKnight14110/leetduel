package com.leetduel.practice.practice;

import com.leetduel.practice.ai.EmbeddingService;
import com.leetduel.practice.dto.RecommendationResponse;
import com.leetduel.practice.repository.PracticeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private PracticeRepository practiceRepository;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ObjectMapper objectMapper;

    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any())).thenReturn(null);
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");
        recommendationService = new RecommendationService(practiceRepository, embeddingService, redisTemplate, objectMapper);
        setField("cacheSeconds", 600L);
        setField("maxRecommendations", 3);
        setField("maxCandidates", 10);
    }

    @Test
    void prioritizesWeakTopicBeforeAHighSemanticMatchWithoutWeakness() {
        UUID weakTopicProblem = UUID.randomUUID();
        UUID semanticProblem = UUID.randomUUID();
        when(practiceRepository.getWeakTags(any())).thenReturn(List.of("arrays"));
        when(embeddingService.queryVector(any())).thenReturn("[0.1,0.2]");
        when(practiceRepository.findCandidates(any(), any(), anyInt())).thenReturn(List.of(
                new PracticeRepository.RecommendationCandidate(weakTopicProblem, "array-rep", "Array Rep", "EASY",
                        List.of("arrays"), 0.20, 2, 0),
                new PracticeRepository.RecommendationCandidate(semanticProblem, "graph-rep", "Graph Rep", "MEDIUM",
                        List.of("graphs"), 0.80, 0, 0)));

        List<RecommendationResponse> recommendations = recommendationService.getRecommendations(UUID.randomUUID());

        assertThat(recommendations).extracting(RecommendationResponse::problemId).containsExactly(weakTopicProblem, semanticProblem);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = RecommendationService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(recommendationService, value);
    }
}
