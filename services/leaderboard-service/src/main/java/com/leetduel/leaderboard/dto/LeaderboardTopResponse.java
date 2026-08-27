package com.leetduel.leaderboard.dto;

import com.leetduel.leaderboard.board.Board;

import java.util.List;

public record LeaderboardTopResponse(Board board, List<LeaderboardEntry> entries) {
}
