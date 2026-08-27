package com.leetduel.matchmaking;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

// First Testcontainers usage in this repo, deliberately scoped to this one
// class - see the Phase 2 plan's Testing section for why: the claim behind
// pair_match.lua ("two concurrent sweeps cannot double-book a player") is
// unverifiable against a mocked RedisTemplate. Only a real Redis and real
// concurrent threads can actually falsify it.
@Testcontainers
class PairingLuaScriptIT {

    private static final String POOL_KEY = "matchmaking:{pool}";
    private static final String WAIT_START_KEY = "matchmaking:{pool}:wait_start";
    private static final int PLAYER_COUNT = 40;

    private static GenericContainer<?> redis;
    private static StringRedisTemplate redisTemplate;
    private static RedisScript<List<Object>> pairMatchScript;

    @BeforeAll
    static void startRedis() {
        redis = new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);
        redis.start();

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                redis.getHost(), redis.getMappedPort(6379));
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.afterPropertiesSet();

        // Same erasure-forced raw-type-then-cast shape as RedisConfig's
        // production beans - see that class's comment.
        @SuppressWarnings({"unchecked", "rawtypes"})
        RedisScript<List<Object>> script = (RedisScript)
                RedisScript.of(new ClassPathResource("scripts/pair_match.lua"), List.class);
        pairMatchScript = script;
    }

    @AfterAll
    static void stopRedis() {
        redis.stop();
    }

    @Test
    void concurrentPairingNeverDoubleBooksAPlayer() throws InterruptedException {
        // Arrange: PLAYER_COUNT players, all the same ELO, all in the pool
        // at once - deliberately maximal contention, every thread is a
        // valid opponent for every other thread.
        for (int i = 0; i < PLAYER_COUNT; i++) {
            String userId = "player-" + i;
            redisTemplate.opsForZSet().add(POOL_KEY, userId, 1200);
            redisTemplate.opsForHash().put(WAIT_START_KEY, userId, String.valueOf(System.currentTimeMillis()));
        }

        ExecutorService executor = Executors.newFixedThreadPool(PLAYER_COUNT);
        CountDownLatch startLine = new CountDownLatch(1);
        List<String[]> matchedPairs = new ArrayList<>();
        Object resultsLock = new Object();
        AtomicInteger noOpCount = new AtomicInteger();

        List<Runnable> tasks = IntStream.range(0, PLAYER_COUNT)
                .<Runnable>mapToObj(i -> () -> {
                    String userId = "player-" + i;
                    try {
                        startLine.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    // Wide window - every other player is a valid candidate,
                    // maximizing the chance two threads race for the same
                    // opponent.
                    List<?> result = redisTemplate.execute(pairMatchScript,
                            List.of(POOL_KEY, WAIT_START_KEY),
                            List.of(userId, "1200", "0", "3000"));
                    if ("1".equals(String.valueOf(result.get(0)))) {
                        String opponentId = String.valueOf(result.get(1));
                        synchronized (resultsLock) {
                            matchedPairs.add(new String[] {userId, opponentId});
                        }
                    } else {
                        noOpCount.incrementAndGet();
                    }
                })
                .toList();

        for (Runnable task : tasks) {
            executor.submit(task);
        }
        startLine.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // Assert: the core atomicity claim - every player appears in AT
        // MOST one successful pairing. If pair_match.lua's atomicity were
        // broken, a player could be handed out as the "opponent" in two
        // different EVAL calls before either removed them from the pool.
        Set<String> seenPlayers = ConcurrentHashMap.newKeySet();
        for (String[] pair : matchedPairs) {
            for (String playerId : pair) {
                boolean firstSighting = seenPlayers.add(playerId);
                assertThat(firstSighting)
                        .as("player %s was double-booked into more than one match", playerId)
                        .isTrue();
            }
        }

        // Every successful pairing consumes exactly 2 players; the pool
        // must reflect that - no player silently duplicated or dropped.
        assertThat(matchedPairs.size() * 2).isEqualTo(seenPlayers.size());
        Long remainingInPool = redisTemplate.opsForZSet().size(POOL_KEY);
        assertThat(remainingInPool).isEqualTo(PLAYER_COUNT - seenPlayers.size());
    }
}
