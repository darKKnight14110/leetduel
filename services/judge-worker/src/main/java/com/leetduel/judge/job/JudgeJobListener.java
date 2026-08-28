package com.leetduel.judge.job;

import com.leetduel.judge.event.SubmissionJudgedEvent;
import com.leetduel.judge.sandbox.DockerSandboxService;
import com.leetduel.judge.sandbox.SandboxSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("docker")
@RequiredArgsConstructor
public class JudgeJobListener {

    private final DockerSandboxService sandboxService;
    private final JudgeEngine judgeEngine;
    private final RabbitTemplate rabbitTemplate;

    @Value("${leetduel.events.judge-events-exchange}")
    private String judgeEventsExchange;

    @Value("${leetduel.events.submission-judged-routing-key}")
    private String submissionJudgedRoutingKey;

    @RabbitListener(queues = "${leetduel.events.judge-jobs-queue}")
    public void onJudgeJob(JudgeJobCreatedEvent job) {
        try {
            try (SandboxSession sandbox = sandboxService.open(judgeEngine.sandboxImage(job.language()))) {
                publishResult(judgeEngine.judge(job, sandbox));
            }
        } catch (Exception e) {
            log.error("Judging failed for submission {}", job.submissionId(), e);
            publishResult(judgeEngine.internalError(job));
        }
    }

    private void publishResult(SubmissionJudgedEvent event) {
        rabbitTemplate.convertAndSend(judgeEventsExchange, submissionJudgedRoutingKey, event);
    }
}
