package com.leetduel.leaderboard.board;

import com.leetduel.leaderboard.config.LeaderboardKeys;
import com.leetduel.leaderboard.dto.LeaderboardEntry;
import com.leetduel.leaderboard.dto.LeaderboardTopResponse;
import com.leetduel.leaderboard.dto.RankResponse;
import com.leetduel.leaderboard.dto.RankWindowResponse;
import com.leetduel.leaderboard.event.MatchCompletedEvent;
import com.leetduel.leaderboard.exception.UserNotRankedException;
import com.leetduel.leaderboard.period.PeriodKeyResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// This service holds NO source-of-truth data - every row here is derived
// entirely from match.completed events already applied durably elsewhere
// (duel-service's Postgres match record, user-service's ELO write). That
// makes it a materialized read-model / CQRS-style projection: rebuildable
// from scratch by replaying history, never the thing anything else depends
// on for correctness.
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List<Object>> incrementPeriodScoreScript;

    @Value("${leetduel.leaderboard.idempotency-marker-ttl-seconds}")
    private long idempotencyMarkerTtlSeconds;

    @Value("${leetduel.leaderboard.weekly-key-ttl-seconds}")
    private long weeklyKeyTtlSeconds;

    @Value("${leetduel.leaderboard.season-key-ttl-seconds}")
    private long seasonKeyTtlSeconds;

    public void applyMatchResult(MatchCompletedEvent event) {
        Instant now = Instant.now();
        String weekKey = PeriodKeyResolver.isoWeekKey(now);
        String quarterKey = PeriodKeyResolver.quarterKey(now);

        applyResultToPlayer(event.matchId(), event.player1Id(), event.player1EloAtMatch(), event.player1EloDelta(),
                weekKey, quarterKey);
        applyResultToPlayer(event.matchId(), event.player2Id(), event.player2EloAtMatch(), event.player2EloDelta(),
                weekKey, quarterKey);
    }

    private void applyResultToPlayer(UUID matchId, UUID playerId, int eloAtMatch, int eloDelta, String weekKey,
            String quarterKey) {
        // Absolute value, simple addition (not re-deriving duel-service's
        // logistic ELO formula) - see docs/goals.md's Phase 4 plan for why
        // this makes ZADD naturally idempotent under at-least-once
        // redelivery, needing no dedup guard: replaying the same event
        // just sets the same score twice.
        long absoluteElo = eloAtMatch + eloDelta;
        redisTemplate.opsForZSet().add(LeaderboardKeys.GLOBAL, playerId.toString(), absoluteElo);

        incrementPeriod(weekKey, LeaderboardKeys.weekly(weekKey), matchId, playerId, eloDelta, weeklyKeyTtlSeconds);
        incrementPeriod(quarterKey, LeaderboardKeys.season(quarterKey), matchId, playerId, eloDelta,
                seasonKeyTtlSeconds);
    }

    private void incrementPeriod(String period, String periodKey, UUID matchId, UUID playerId, int eloDelta,
            long periodTtlSeconds) {
        String markerKey = LeaderboardKeys.appliedMarker(period, matchId, playerId);
        redisTemplate.execute(incrementPeriodScoreScript, List.of(periodKey, markerKey),
                playerId.toString(), String.valueOf(eloDelta), String.valueOf(idempotencyMarkerTtlSeconds),
                String.valueOf(periodTtlSeconds));
    }

    public LeaderboardTopResponse getTop(Board board, int limit) {
        String key = currentKeyFor(board);
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(key, 0, limit - 1L);

        List<LeaderboardEntry> entries = List.of();
        if (tuples != null) {
            int[] rank = {1};
            entries = tuples.stream()
                    .map(t -> new LeaderboardEntry(UUID.fromString(t.getValue()), t.getScore().longValue(), rank[0]++))
                    .collect(Collectors.toList());
        }
        return new LeaderboardTopResponse(board, entries);
    }

    public RankResponse getRank(Board board, UUID userId) {
        String key = currentKeyFor(board);
        Long zeroBasedRank = redisTemplate.opsForZSet().reverseRank(key, userId.toString());
        if (zeroBasedRank == null) {
            throw new UserNotRankedException("User " + userId + " is not ranked on the " + board + " board");
        }
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());
        return new RankResponse(board, userId, zeroBasedRank.intValue() + 1, score == null ? 0 : score.longValue());
    }

    // Rank-relative windowing: find the user's index via ZREVRANK, then
    // range around that index - a different access pattern than getTop's
    // fixed range from the top (see RankWindowResponse's comment).
    public RankWindowResponse getRankWindow(Board board, UUID userId, int window) {
        String key = currentKeyFor(board);
        Long zeroBasedRank = redisTemplate.opsForZSet().reverseRank(key, userId.toString());
        if (zeroBasedRank == null) {
            throw new UserNotRankedException("User " + userId + " is not ranked on the " + board + " board");
        }

        long start = Math.max(0, zeroBasedRank - window);
        long end = zeroBasedRank + window;
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);

        List<LeaderboardEntry> entries = List.of();
        if (tuples != null) {
            long[] rank = {start + 1};
            entries = tuples.stream()
                    .map(t -> new LeaderboardEntry(UUID.fromString(t.getValue()), t.getScore().longValue(),
                            (int) rank[0]++))
                    .collect(Collectors.toList());
        }
        return new RankWindowResponse(board, userId, zeroBasedRank.intValue() + 1, entries);
    }

    // Weekly/season boards always resolve to the CURRENT period - there is
    // no historical "show me week 2026-W20" query in this phase's scope
    // (see docs/goals.md's Phase 4 plan, "Out of scope").
    private String currentKeyFor(Board board) {
        Instant now = Instant.now();
        return switch (board) {
            case GLOBAL -> LeaderboardKeys.GLOBAL;
            case WEEKLY -> LeaderboardKeys.weekly(PeriodKeyResolver.isoWeekKey(now));
            case SEASON -> LeaderboardKeys.season(PeriodKeyResolver.quarterKey(now));
        };
    }
}
