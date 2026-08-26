package com.leetduel.submission.outbox;

import com.leetduel.submission.event.JudgeJobCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

// Identical pattern to auth-service's OutboxRelay - polling, not CDC/
// Debezium, same reasoning (a few seconds of latency is acceptable at this
// scale, avoids reintroducing Kafka-adjacent infra). Publishes to the
// DIRECT judge.jobs.exchange rather than a topic exchange - exactly one
// consumer group (the Judge Worker pool) ever wants a job.
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${leetduel.events.judge-jobs-exchange}")
    private String exchange;

    // Deliberately NOT @Transactional at the method level - see
    // auth-service's OutboxRelay for why (one bad event must retry alone,
    // not roll back publishes already committed for events ahead of it).
    @Scheduled(fixedDelayString = "${leetduel.outbox.poll-interval-ms}")
    public void relayPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();

        for (OutboxEvent event : pending) {
            try {
                JudgeJobCreatedEvent payload = objectMapper.readValue(event.getPayload(), JudgeJobCreatedEvent.class);
                rabbitTemplate.convertAndSend(exchange, event.getEventType(), payload);
                event.setPublishedAt(Instant.now());
                outboxEventRepository.save(event);
            } catch (Exception e) {
                log.warn("Failed to relay outbox event {}, will retry next poll", event.getId(), e);
            }
        }
    }
}
