package com.leetduel.practice.practice;

import com.leetduel.practice.event.PracticeSubmissionCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PracticeEventListener {

    private final PracticeProgressService practiceProgressService;
    private final RecommendationService recommendationService;
    private final ExplanationService explanationService;

    @RabbitListener(queues = "${leetduel.events.practice-queue}")
    public void onPracticeSubmissionCompleted(PracticeSubmissionCompletedEvent event) {
        if (practiceProgressService.record(event)) {
            recommendationService.invalidate(event.userId());
            explanationService.generateHint(event.submissionId());
        }
    }
}
