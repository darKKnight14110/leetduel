package com.leetduel.leaderboard.event;

import com.leetduel.leaderboard.board.LeaderboardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchCompletedListenerTest {

    @Mock
    private LeaderboardService leaderboardService;

    @Test
    void onMatchCompleted_delegatesToLeaderboardService() {
        // Arrange
        MatchCompletedListener listener = new MatchCompletedListener(leaderboardService);
        MatchCompletedEvent event = new MatchCompletedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, true, 1200, 1200, 0, 0);

        // Act
        listener.onMatchCompleted(event);

        // Assert - thin listener, all logic lives in LeaderboardService so
        // it's testable without a broker (see LeaderboardServiceTest).
        verify(leaderboardService).applyMatchResult(event);
    }
}
