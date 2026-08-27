package com.leetduel.leaderboard;

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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

// Same precedent as matchmaking-service's PairingLuaScriptIT: the
// dedup-then-increment atomicity claim behind increment_period_score.lua
// is unverifiable against a mocked RedisTemplate - only a real Redis and
// real concurrent threads can falsify it.
@Testcontainers
class IncrementScoreLuaScriptIT {

    private static final String PERIOD_KEY = "leaderboard:weekly:2026-W35";
    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    private static GenericContainer<?> redis;
    private static StringRedisTemplate redisTemplate;
    private static RedisScript<List<Object>> incrementScript;

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

        @SuppressWarnings({"unchecked", "rawtypes"})
        RedisScript<List<Object>> script = (RedisScript)
                RedisScript.of(new ClassPathResource("scripts/increment_period_score.lua"), List.class);
        incrementScript = script;
    }

    @AfterAll
    static void stopRedis() {
        redis.stop();
    }

    private static String markerKey(String matchId, String userId) {
        return "leaderboard:applied:2026-W35:" + matchId + ":" + userId;
    }

    @Test
    void concurrentEvalsWithSameMarker_applyExactlyOnce() throws InterruptedException {
        String matchId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        redisTemplate.delete(PERIOD_KEY);
        redisTemplate.delete(markerKey(matchId, USER_ID));

        int concurrency = 20;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLine = new CountDownLatch(1);
        AtomicInteger appliedCount = new AtomicInteger();

        List<Runnable> tasks = IntStream.range(0, concurrency)
                .<Runnable>mapToObj(i -> () -> {
                    try {
                        startLine.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    List<?> result = redisTemplate.execute(incrementScript,
                            List.of(PERIOD_KEY, markerKey(matchId, USER_ID)),
                            USER_ID, "25", "604800", "1209600");
                    if ("1".equals(String.valueOf(result.get(0)))) {
                        appliedCount.incrementAndGet();
                    }
                })
                .toList();

        tasks.forEach(executor::submit);
        startLine.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // Core atomicity claim: however many threads raced to apply the
        // SAME (matchId, userId, period) delta, exactly one of them
        // actually did - the rest observed the marker and no-opped.
        assertThat(appliedCount.get()).isEqualTo(1);
        Double score = redisTemplate.opsForZSet().score(PERIOD_KEY, USER_ID);
        assertThat(score).isEqualTo(25.0);
    }

    @Test
    void sequentialEvalsWithDifferentMatchIds_accumulateAdditively() {
        String periodKey = "leaderboard:weekly:2026-W36";
        redisTemplate.delete(periodKey);

        String matchA = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
        String matchB = "cccccccc-cccc-cccc-cccc-cccccccccccc";
        redisTemplate.delete(markerKey("2026-W36", matchA));
        redisTemplate.execute(incrementScript,
                List.of(periodKey, "leaderboard:applied:2026-W36:" + matchA + ":" + USER_ID),
                USER_ID, "20", "604800", "1209600");
        redisTemplate.execute(incrementScript,
                List.of(periodKey, "leaderboard:applied:2026-W36:" + matchB + ":" + USER_ID),
                USER_ID, "-8", "604800", "1209600");

        Double score = redisTemplate.opsForZSet().score(periodKey, USER_ID);
        assertThat(score).isEqualTo(12.0);
    }

    @Test
    void secondEvalAgainstAlreadyTtlDKey_doesNotResetTtl() {
        String periodKey = "leaderboard:weekly:2026-W37";
        String matchA = "dddddddd-dddd-dddd-dddd-dddddddddddd";
        String matchB = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee";
        redisTemplate.delete(periodKey);

        redisTemplate.execute(incrementScript,
                List.of(periodKey, "leaderboard:applied:2026-W37:" + matchA + ":" + USER_ID),
                USER_ID, "10", "604800", "50");
        Long firstTtl = redisTemplate.getExpire(periodKey);
        assertThat(firstTtl).isGreaterThan(0);

        // A second match landing in the same period passes a much larger
        // TTL - if EXPIRE ... NX weren't in play, this would push the
        // key's expiry far out. It must not: only the FIRST writer to a
        // period key ever sets its expiry.
        redisTemplate.execute(incrementScript,
                List.of(periodKey, "leaderboard:applied:2026-W37:" + matchB + ":" + USER_ID),
                USER_ID, "5", "604800", "999999");
        Long secondTtl = redisTemplate.getExpire(periodKey);

        assertThat(secondTtl).isLessThanOrEqualTo(firstTtl);
    }
}
