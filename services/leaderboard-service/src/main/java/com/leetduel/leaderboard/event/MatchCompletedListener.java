package com.leetduel.leaderboard.event;

import com.leetduel.leaderboard.board.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

// Consumes duel-service's match.completed off the SAME match.events topic
// exchange every other consumer in this repo binds to - see
// config/RabbitConfig for the binding itself. Thin by design: all the
// actual board-update logic lives in LeaderboardService so it can be unit
// tested without a broker.
@Component
@RequiredArgsConstructor
public class MatchCompletedListener {

    private final LeaderboardService leaderboardService;

    @RabbitListener(queues = "${leetduel.events.leaderboard-match-completed-queue}")
    public void onMatchCompleted(MatchCompletedEvent event) {
        leaderboardService.applyMatchResult(event);
    }
}
