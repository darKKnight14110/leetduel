package com.leetduel.matchmaking.queue;

import com.leetduel.matchmaking.client.UserServiceClient;
import com.leetduel.matchmaking.config.RedisKeys;
import com.leetduel.matchmaking.dto.QueueState;
import com.leetduel.matchmaking.dto.QueueStatusResponse;
import com.leetduel.matchmaking.event.JoinRequestedEvent;
import com.leetduel.matchmaking.exception.QueuePublishUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QueueService {

    private final UserServiceClient userServiceClient;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List<Object>> leaveQueueScript;

    @Value("${leetduel.events.matchmaking-jobs-exchange}")
    private String joinExchange;

    @Value("${leetduel.events.matchmaking-join-routing-key}")
    private String joinRoutingKey;

    @Value("${leetduel.matchmaking.waiting-status-ttl-seconds}")
    private long waitingStatusTtlSeconds;

    public void join(UUID userId) {
        // Never trust a client-supplied ELO - a client lying about its own
        // rating to get matched against easier opponents is exactly the
        // integrity issue this synchronous lookup closes.
        int elo = userServiceClient.getElo(userId);
        JoinRequestedEvent event = new JoinRequestedEvent(userId, elo, Instant.now().toEpochMilli());
        try {
            rabbitTemplate.convertAndSend(joinExchange, joinRoutingKey, event);
        } catch (AmqpException e) {
            throw new QueuePublishUnavailableException("Could not enqueue join request for " + userId, e);
        }

        // Optimistic UX marker only, NOT pairing-critical state - the pool
        // ZSET / wait-start Hash stay writer-restricted to
        // JoinRequestListener. If this write is ever stale relative to
        // that, worst case is a client's poll briefly shows a stale value,
        // never a double-booked or dropped match.
        String statusKey = RedisKeys.status(userId);
        redisTemplate.opsForHash().put(statusKey, "state", QueueState.WAITING.name());
        redisTemplate.expire(statusKey, Duration.ofSeconds(waitingStatusTtlSeconds));
    }

    public QueueStatusResponse getStatus(UUID userId) {
        Map<Object, Object> status = redisTemplate.opsForHash().entries(RedisKeys.status(userId));
        if (status.isEmpty()) {
            return new QueueStatusResponse(QueueState.NEVER_JOINED, null);
        }
        UUID matchId = status.containsKey("matchId") ? UUID.fromString((String) status.get("matchId")) : null;
        return new QueueStatusResponse(QueueState.valueOf((String) status.get("state")), matchId);
    }

    public QueueStatusResponse leave(UUID userId) {
        List<?> result = redisTemplate.execute(leaveQueueScript,
                List.of(RedisKeys.POOL, RedisKeys.WAIT_START, RedisKeys.status(userId)),
                List.of(userId.toString()));

        boolean cancelled = "1".equals(String.valueOf(result.get(0)));
        if (cancelled) {
            return new QueueStatusResponse(QueueState.NEVER_JOINED, null);
        }
        Object matchId = result.size() > 1 ? result.get(1) : null;
        return new QueueStatusResponse(QueueState.MATCHED, matchId == null ? null : UUID.fromString(matchId.toString()));
    }
}
