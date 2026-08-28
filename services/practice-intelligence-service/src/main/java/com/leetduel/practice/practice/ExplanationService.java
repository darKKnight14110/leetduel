package com.leetduel.practice.practice;

import com.leetduel.practice.ai.NvidiaClient;
import com.leetduel.practice.dto.ExplanationContent;
import com.leetduel.practice.event.PracticeExplanationReadyEvent;
import com.leetduel.practice.repository.PracticeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExplanationService {

    private final PracticeRepository practiceRepository;
    private final NvidiaClient nvidiaClient;
    private final RabbitTemplate rabbitTemplate;
    @Qualifier("practiceTaskExecutor")
    private final Executor practiceTaskExecutor;

    @Value("${leetduel.events.practice-exchange}")
    private String practiceExchange;

    @Value("${leetduel.events.practice-explanation-routing-key}")
    private String explanationRoutingKey;

    @Value("${leetduel.practice.explanation-retention-days}")
    private int retentionDays;

    @Async("practiceTaskExecutor")
    public void generateHint(UUID submissionId) {
        if (!practiceRepository.claimHint(submissionId)) {
            return;
        }
        try {
            PracticeRepository.ExplanationInput input = practiceRepository.getExplanationInput(submissionId);
            ExplanationContent content = nvidiaClient.explain(input, false);
            practiceRepository.saveHint(submissionId, content);
            publishReady(input, "HINT_READY");
        } catch (RuntimeException exception) {
            practiceRepository.failHint(submissionId, exception.getMessage());
        }
    }

    @Async("practiceTaskExecutor")
    public void generateWalkthrough(UUID submissionId) {
        try {
            PracticeRepository.ExplanationInput input = practiceRepository.getExplanationInput(submissionId);
            ExplanationContent content = nvidiaClient.explain(input, true);
            practiceRepository.saveWalkthrough(submissionId, content);
            publishReady(input, "WALKTHROUGH_READY");
        } catch (RuntimeException exception) {
            practiceRepository.failWalkthrough(submissionId, exception.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${leetduel.practice.explanation-retry-delay-ms:30000}")
    public void retryQueuedHints() {
        for (UUID submissionId : practiceRepository.findRetryableHints(10)) {
            practiceTaskExecutor.execute(() -> generateHint(submissionId));
        }
        practiceRepository.deleteExpiredSourceCode(retentionDays);
    }

    private void publishReady(PracticeRepository.ExplanationInput input, String status) {
        rabbitTemplate.convertAndSend(practiceExchange, explanationRoutingKey,
                new PracticeExplanationReadyEvent(input.submissionId(), input.userId(), status, java.time.Instant.now()));
    }
}
