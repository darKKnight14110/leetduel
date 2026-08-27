package com.leetduel.leaderboard.dto;

import com.leetduel.leaderboard.board.Board;

import java.util.List;
import java.util.UUID;

// entries are the requesting user's own rank plus up to `window` players
// immediately above and below - a rank-relative windowing query, distinct
// from getTop's fixed range-from-the-top pagination (see docs/goals.md's
// Phase 4 plan on why this is a deliberately different Redis access
// pattern worth having, not just top-N with an offset).
public record RankWindowResponse(Board board, UUID userId, int rank, List<LeaderboardEntry> entries) {
}
