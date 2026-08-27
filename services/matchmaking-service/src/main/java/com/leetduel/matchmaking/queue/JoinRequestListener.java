package com.leetduel.matchmaking.queue;

import com.leetduel.matchmaking.config.RedisKeys;
import com.leetduel.matchmaking.dto.QueueState;
import com.leetduel.matchmaking.event.JoinRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

// The ONLY writer of the pool ZSET / wait-start Hash - QueueService's
// join() only publishes the durable message and sets an optimistic status
// marker, it never touches pool state directly. Idempotent against
// redelivery (RabbitMQ is at-least-once): a duplicate delivery after the
// user is already MATCHED/EXPIRED is a no-op, never a re-insert into the
// pool, and putIfAbsent on wait-start means a redelivery never resets a
// user's fairness clock. Same defensive shape as submission-service's
// SubmissionJudgedListener.
@Component
@RequiredArgsConstructor
@Slf4j
public class JoinRequestListener {

    private final StringRedisTemplate redisTemplate;

    @RabbitListener(queues = "${leetduel.events.matchmaking-join-queue}")
    public void onJoinRequested(JoinRequestedEvent event) {
        String statusKey = RedisKeys.status(event.userId());
        Object state = redisTemplate.opsForHash().get(statusKey, "state");
        if (QueueState.MATCHED.name().equals(state) || QueueState.EXPIRED.name().equals(state)) {
            log.debug("Join request for {} is stale ({}), skipping duplicate delivery", event.userId(), state);
            return;
        }

        redisTemplate.opsForZSet().add(RedisKeys.POOL, event.userId().toString(), event.elo());
        redisTemplate.opsForHash().putIfAbsent(RedisKeys.WAIT_START, event.userId().toString(),
                String.valueOf(event.requestedAtEpochMillis()));
        redisTemplate.opsForHash().put(statusKey, "state", QueueState.WAITING.name());
    }
}
