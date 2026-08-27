package com.leetduel.leaderboard.dto;

import com.leetduel.leaderboard.board.Board;

import java.util.UUID;

public record RankResponse(Board board, UUID userId, int rank, long score) {
}
