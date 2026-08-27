package com.leetduel.duel.outbox;

import com.leetduel.duel.event.DuelProgressEvent;
import com.leetduel.duel.event.MatchCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

// Identical polling pattern to every other service's OutboxRelay. Unlike
// matchmaking-service's (which only ever publishes MatchCreatedEvent), this
// service writes TWO distinct event types through the same outbox table -
// event_type doubles as both the routing key AND the discriminator for
// which Java DTO to deserialize into before re-publishing (so the AMQP
// message carries the right __TypeId__ header for the consumer side's
// JacksonJsonMessageConverter).
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${leetduel.events.match-events-exchange}")
    private String exchange;

    // Deliberately NOT @Transactional at the method level - one bad event
    // must retry alone, not roll back publishes already committed for
    // events ahead of it. Same reasoning as every other OutboxRelay.
    @Scheduled(fixedDelayString = "${leetduel.outbox.poll-interval-ms}")
    public void relayPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();

        for (OutboxEvent event : pending) {
            try {
                Object payload = switch (event.getEventType()) {
                    case "duel.progress" -> objectMapper.readValue(event.getPayload(), DuelProgressEvent.class);
                    case "match.completed" -> objectMapper.readValue(event.getPayload(), MatchCompletedEvent.class);
                    default -> throw new IllegalStateException("Unknown outbox event_type: " + event.getEventType());
                };
                rabbitTemplate.convertAndSend(exchange, event.getEventType(), payload);
                event.setPublishedAt(Instant.now());
                outboxEventRepository.save(event);
            } catch (Exception e) {
                log.warn("Failed to relay outbox event {}, will retry next poll", event.getId(), e);
            }
        }
    }
}
