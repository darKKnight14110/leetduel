package com.leetduel.auth.outbox;

import com.leetduel.auth.event.UserCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${leetduel.events.user-exchange}")
    private String exchange;

    // Polling, not Debezium/CDC: CDC (reading the DB's write-ahead log
    // directly) is the textbook production answer - near-zero latency, no
    // repeated query load - but it means running Kafka Connect or a
    // Debezium server, which reintroduces the Kafka-adjacent infra this
    // project deliberately dropped as overkill for its scale. A short poll
    // interval gets the same durability guarantee with a few seconds of
    // added latency and zero extra infrastructure - the right trade at this
    // scale, revisit if it ever isn't.
    // Deliberately NOT @Transactional at the method level: each iteration's
    // save() already gets its own transaction from Spring Data JPA. Wrapping
    // the whole batch in one transaction would hold a DB connection open
    // across every RabbitMQ network call in the loop, and - worse - a
    // failure on event N would roll back the publish confirmations already
    // committed for events 1..N-1. Per-row independence is exactly what's
    // wanted here: one bad event retries alone, the rest of the batch
    // still succeeds.
    @Scheduled(fixedDelayString = "${leetduel.outbox.poll-interval-ms}")
    public void relayPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();

        for (OutboxEvent event : pending) {
            try {
                // Deserialize back to the typed event and let the configured
                // JacksonJsonMessageConverter serialize it - passing the
                // already-JSON payload String directly would make the
                // converter re-serialize the string itself, double-encoding it.
                UserCreatedEvent payload = objectMapper.readValue(event.getPayload(), UserCreatedEvent.class);
                rabbitTemplate.convertAndSend(exchange, event.getEventType(), payload);
                event.setPublishedAt(Instant.now());
                outboxEventRepository.save(event);
            } catch (Exception e) {
                // Leave published_at null - next poll retries this row.
                // If the publish actually succeeded and only the
                // "mark published" write failed, the next poll re-publishes
                // it: a duplicate delivery, which is exactly why the
                // consumer side (UserCreatedListener) is idempotent.
                log.warn("Failed to relay outbox event {}, will retry next poll", event.getId(), e);
            }
        }
    }
}
