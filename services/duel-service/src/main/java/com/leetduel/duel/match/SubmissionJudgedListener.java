package com.leetduel.duel.match;

import com.leetduel.duel.event.SubmissionJudgedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

// Consumes the SAME judge.events topic exchange submission-service already
// binds its own queue to - zero topology change on Judge Worker's producer
// side, exactly as anticipated in that service's RabbitConfig comment.
@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionJudgedListener {

    private final MatchService matchService;

    @RabbitListener(queues = "${leetduel.events.duel-service-submission-judged-queue}")
    public void onSubmissionJudged(SubmissionJudgedEvent event) {
        if (event.matchId() == null) {
            // Practice-mode submission - Duel Service has nothing to do
            // with it. Every practice submission flows through this same
            // exchange; this filter is the only thing that separates them.
            return;
        }

        try {
            matchService.applyProgress(event);
        } catch (ObjectOptimisticLockingFailureException e) {
            // Match was completed by a concurrent writer (opponent's own
            // 100% landing at the same instant, or the timeout sweep)
            // between this listener's read and write - the other writer's
            // outcome already stands, so a duplicate progress update here
            // is a no-op, not an error. See Match.version's comment.
            log.debug("Match {} was completed concurrently, dropping this progress update", event.matchId());
        }
    }
}
