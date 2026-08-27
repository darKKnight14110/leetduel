package com.leetduel.duel.match;

import com.leetduel.duel.event.MatchCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MatchCreatedListener {

    private final MatchService matchService;

    @RabbitListener(queues = "${leetduel.events.duel-service-match-created-queue}")
    public void onMatchCreated(MatchCreatedEvent event) {
        matchService.createMatch(event);
    }
}
