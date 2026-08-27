package com.leetduel.leaderboard.dto;

import java.util.UUID;

// rank is 1-based (ZREVRANK is 0-based; +1 happens at the read boundary,
// never stored) - matches how a leaderboard is actually displayed.
public record LeaderboardEntry(UUID userId, long score, int rank) {
}
