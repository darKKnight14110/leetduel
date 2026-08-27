package com.leetduel.duel.match;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

// Closes any match whose time limit has elapsed with no player at 100% -
// higher progress wins, exact tie is a draw. Runs on every instance once
// horizontally scaled; safe by construction because completeOnTimeout's
// optimistic-lock guard makes a redundant concurrent sweep a no-op, same
// "cheap redundant work, no distributed lock needed" posture as
// matchmaking-service's MatchmakingSweepScheduler.
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchTimeoutSweeper {

    private final MatchRepository matchRepository;
    private final MatchService matchService;

    @Scheduled(fixedDelayString = "${leetduel.duel.sweep.fixed-delay-ms}")
    public void sweep() {
        List<Match> inProgress = matchRepository.findByStatus(MatchStatus.IN_PROGRESS);
        Instant now = Instant.now();

        for (Match match : inProgress) {
            Instant deadline = match.getStartedAt().plusMillis(match.getTimeLimitMs());
            if (now.isBefore(deadline)) {
                continue;
            }
            try {
                matchService.completeOnTimeout(match.getId());
            } catch (ObjectOptimisticLockingFailureException e) {
                log.debug("Match {} was already completed concurrently, skipping timeout close", match.getId());
            }
        }
    }
}
