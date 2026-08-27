package com.leetduel.matchmaking.queue;

import com.leetduel.matchmaking.client.ProblemServiceClient;
import com.leetduel.matchmaking.config.RedisKeys;
import com.leetduel.matchmaking.dto.QueueState;
import com.leetduel.matchmaking.match.MatchWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// The only place actual pairing happens - joining just gets a user into
// the pool (JoinRequestListener); this periodic sweep is what matches them.
// Runs on every instance once horizontally scaled - safe by construction
// because pair_match.lua/expire_join.lua are self-resolving atomic steps
// (see their header comments), not because of any distributed lock here.
// Redundant concurrent sweeps across instances just do a few extra no-op
// EVAL calls, which is cheap and deliberately preferred over introducing
// ShedLock/leader-election as new infra this project doesn't otherwise need.
@Component
@RequiredArgsConstructor
@Slf4j
public class MatchmakingSweepScheduler {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> pairMatchScript;
    private final RedisScript<List> expireJoinScript;
    private final ProblemServiceClient problemServiceClient;
    private final MatchWriter matchWriter;

    @Value("${leetduel.matchmaking.window.base-elo}")
    private int baseElo;

    @Value("${leetduel.matchmaking.window.growth-elo-per-second}")
    private double growthEloPerSecond;

    @Value("${leetduel.matchmaking.window.cap-elo}")
    private int capElo;

    @Value("${leetduel.matchmaking.max-wait-ms}")
    private long maxWaitMs;

    @Value("${leetduel.matchmaking.expired-marker-ttl-seconds}")
    private long expiredMarkerTtlSeconds;

    @Value("${leetduel.matchmaking.duel-time-limit-ms}")
    private int duelTimeLimitMs;

    @Scheduled(fixedDelayString = "${leetduel.matchmaking.sweep.fixed-delay-ms}")
    public void sweep() {
        long now = Instant.now().toEpochMilli();
        Map<Object, Object> waitStarts = redisTemplate.opsForHash().entries(RedisKeys.WAIT_START);
        if (waitStarts.isEmpty()) {
            return;
        }

        // Oldest-first: the longest-waiting user gets first shot at a
        // pairing attempt each pass, matching the fairness intent of the
        // expanding window itself.
        List<Map.Entry<Object, Object>> oldestFirst = waitStarts.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> Long.parseLong((String) e.getValue())))
                .toList();

        // A pairing hit removes TWO entries from this pass's candidate
        // list; tracks which ids this pass has already resolved (matched,
        // expired, or found stale) so it never double-processes the
        // opponent side of a pair it just made.
        Set<String> processed = new HashSet<>();

        for (Map.Entry<Object, Object> entry : oldestFirst) {
            String userId = (String) entry.getKey();
            if (processed.contains(userId)) {
                continue;
            }

            long waitStartMillis = Long.parseLong((String) entry.getValue());
            long waitedMs = now - waitStartMillis;

            if (waitedMs >= maxWaitMs) {
                expire(userId);
                processed.add(userId);
                continue;
            }

            Double elo = redisTemplate.opsForZSet().score(RedisKeys.POOL, userId);
            if (elo == null) {
                // Already matched/expired by a concurrent sweep pass since
                // this entry list was read - harmless, move on.
                processed.add(userId);
                continue;
            }

            double window = Math.min(capElo, baseElo + growthEloPerSecond * (waitedMs / 1000.0));
            List<?> result = redisTemplate.execute(pairMatchScript,
                    List.of(RedisKeys.POOL, RedisKeys.WAIT_START),
                    List.of(userId, String.valueOf(elo), String.valueOf(elo - window), String.valueOf(elo + window)));
            processed.add(userId);

            if ("1".equals(String.valueOf(result.get(0)))) {
                String opponentId = String.valueOf(result.get(1));
                int opponentElo = (int) Double.parseDouble(String.valueOf(result.get(2)));
                processed.add(opponentId);
                handleMatch(UUID.fromString(userId), elo.intValue(), UUID.fromString(opponentId), opponentElo,
                        waitStartMillis);
            }
        }
    }

    private void expire(String userId) {
        redisTemplate.execute(expireJoinScript,
                List.of(RedisKeys.POOL, RedisKeys.WAIT_START, RedisKeys.status(UUID.fromString(userId))),
                List.of(userId, String.valueOf(expiredMarkerTtlSeconds)));
    }

    private void handleMatch(UUID userAId, int userAElo, UUID userBId, int userBElo, long originalWaitStartMillis) {
        try {
            UUID problemId = problemServiceClient.getRandomProblemId();
            UUID matchId = matchWriter.persist(userAId, userAElo, userBId, userBElo, problemId, duelTimeLimitMs);
            markMatched(userAId, matchId);
            markMatched(userBId, matchId);
        } catch (Exception e) {
            // Both users were ALREADY atomically removed from the pool by
            // pair_match.lua before this point - a failure here (problem
            // lookup down, or the DB write fails) must not silently strand
            // them. Re-queue both with their ORIGINAL wait-start timestamp
            // preserved (no fairness reset, status stays WAITING); the
            // next sweep pass retries pairing them, possibly with each
            // other again, possibly with someone else. Same "leave it for
            // the next attempt, never silently drop" posture as the
            // outbox relay's own failure handling.
            log.warn("Match creation failed for {} / {}, re-queueing both", userAId, userBId, e);
            requeue(userAId, userAElo, originalWaitStartMillis);
            requeue(userBId, userBElo, originalWaitStartMillis);
        }
    }

    private void markMatched(UUID userId, UUID matchId) {
        String statusKey = RedisKeys.status(userId);
        redisTemplate.opsForHash().put(statusKey, "state", QueueState.MATCHED.name());
        redisTemplate.opsForHash().put(statusKey, "matchId", matchId.toString());
    }

    private void requeue(UUID userId, int elo, long originalWaitStartMillis) {
        redisTemplate.opsForZSet().add(RedisKeys.POOL, userId.toString(), elo);
        redisTemplate.opsForHash().put(RedisKeys.WAIT_START, userId.toString(), String.valueOf(originalWaitStartMillis));
    }
}
