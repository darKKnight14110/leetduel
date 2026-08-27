package com.leetduel.duel.match;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {

    // Serves the timeout sweep - only ever scans the live subset, backed by
    // idx_matches_in_progress. time_limit_ms varies per row, so the
    // per-match deadline check happens in MatchTimeoutSweeper, not here -
    // same "fetch the live subset, filter in application code" shape as
    // MatchmakingSweepScheduler's Redis hash scan.
    List<Match> findByStatus(MatchStatus status);
}
