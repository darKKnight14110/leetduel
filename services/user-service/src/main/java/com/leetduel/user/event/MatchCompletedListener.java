package com.leetduel.user.event;

import com.leetduel.user.profile.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

// Consumes duel-service's match.completed off the SAME match.events topic
// exchange matchmaking-service's match.created already publishes to - this
// service redeclares the exchange (RabbitMQ dedups identical declarations)
// and binds its own queue/routing-key, exactly like UserCreatedListener
// binds against auth-service's user.events.
@Component
@RequiredArgsConstructor
public class MatchCompletedListener {

    private final UserProfileService userProfileService;

    @RabbitListener(queues = "${leetduel.events.match-completed-queue}")
    public void onMatchCompleted(MatchCompletedEvent event) {
        userProfileService.applyMatchResult(event);
    }
}
