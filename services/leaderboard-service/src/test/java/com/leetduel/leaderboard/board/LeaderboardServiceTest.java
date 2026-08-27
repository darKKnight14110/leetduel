package com.leetduel.leaderboard.board;

import com.leetduel.leaderboard.config.LeaderboardKeys;
import com.leetduel.leaderboard.event.MatchCompletedEvent;
import com.leetduel.leaderboard.period.PeriodKeyResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private RedisScript<List<Object>> incrementPeriodScoreScript;

    private LeaderboardService leaderboardService;

    private void setUp() {
        leaderboardService = new LeaderboardService(redisTemplate, incrementPeriodScoreScript);
        ReflectionTestUtils.setField(leaderboardService, "idempotencyMarkerTtlSeconds", 604800L);
        ReflectionTestUtils.setField(leaderboardService, "weeklyKeyTtlSeconds", 1209600L);
        ReflectionTestUtils.setField(leaderboardService, "seasonKeyTtlSeconds", 10368000L);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @Test
    void applyMatchResult_addsAbsoluteEloToGlobalBoard_forBothPlayers() {
        // Arrange
        setUp();
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        MatchCompletedEvent event = new MatchCompletedEvent(UUID.randomUUID(), player1, player2, player1, false,
                1200, 1180, 16, -16);

        // Act
        leaderboardService.applyMatchResult(event);

        // Assert - ZADD called with eloAtMatch + delta, not either raw value alone.
        verify(zSetOperations).add(LeaderboardKeys.GLOBAL, player1.toString(), 1216.0);
        verify(zSetOperations).add(LeaderboardKeys.GLOBAL, player2.toString(), 1164.0);
    }

    @Test
    void applyMatchResult_invokesIncrementScript_forWeeklyAndSeasonBoards_forBothPlayers() {
        // Arrange
        setUp();
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        MatchCompletedEvent event = new MatchCompletedEvent(matchId, player1, player2, player1, false,
                1200, 1180, 16, -16);

        String weekKey = PeriodKeyResolver.isoWeekKey(Instant.now());
        String quarterKey = PeriodKeyResolver.quarterKey(Instant.now());

        // Act
        leaderboardService.applyMatchResult(event);

        // Assert - 4 invocations total: 2 players x (weekly, season).
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate, times(4)).execute(eq(incrementPeriodScoreScript), keysCaptor.capture(), any(), any(), any(), any());

        List<List<String>> allKeys = keysCaptor.getAllValues();
        assertThat(allKeys).anySatisfy(keys -> assertThat(keys.get(0)).isEqualTo(LeaderboardKeys.weekly(weekKey)));
        assertThat(allKeys).anySatisfy(keys -> assertThat(keys.get(0)).isEqualTo(LeaderboardKeys.season(quarterKey)));
    }

    @Test
    void applyMatchResult_passesEloDeltaAsScriptArgument_notAbsoluteElo() {
        // Arrange
        setUp();
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        MatchCompletedEvent event = new MatchCompletedEvent(UUID.randomUUID(), player1, player2, player1, false,
                1200, 1180, 16, -16);

        // Act
        leaderboardService.applyMatchResult(event);

        // Assert - the delta (16 / -16), not the absolute post-match ELO, is
        // what flows into ZINCRBY via the script's ARGV[2]. Player1's delta
        // (16) is applied to both their weekly and season keys; player2's
        // delta (-16) is applied to their own weekly and season keys.
        verify(redisTemplate, times(2)).execute(eq(incrementPeriodScoreScript), any(),
                any(), eq("16"), any(), any());
        verify(redisTemplate, times(2)).execute(eq(incrementPeriodScoreScript), any(),
                any(), eq("-16"), any(), any());
    }

    @Test
    void getTop_assignsOneBasedRanksInDescendingScoreOrder() {
        // Arrange
        setUp();
        UUID top = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(zSetOperations.reverseRangeWithScores(LeaderboardKeys.GLOBAL, 0, 1))
                .thenReturn(orderedTuples(top, 1500, second, 1400));

        // Act
        var response = leaderboardService.getTop(Board.GLOBAL, 2);

        // Assert
        assertThat(response.entries()).hasSize(2);
        assertThat(response.entries().get(0).userId()).isEqualTo(top);
        assertThat(response.entries().get(0).rank()).isEqualTo(1);
        assertThat(response.entries().get(1).userId()).isEqualTo(second);
        assertThat(response.entries().get(1).rank()).isEqualTo(2);
    }

    private static java.util.LinkedHashSet<ZSetOperations.TypedTuple<String>> orderedTuples(UUID id1, double score1,
            UUID id2, double score2) {
        java.util.LinkedHashSet<ZSetOperations.TypedTuple<String>> set = new java.util.LinkedHashSet<>();
        set.add(ZSetOperations.TypedTuple.of(id1.toString(), score1));
        set.add(ZSetOperations.TypedTuple.of(id2.toString(), score2));
        return set;
    }
}
