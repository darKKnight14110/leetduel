package com.leetduel.leaderboard.web;

import com.leetduel.leaderboard.board.Board;
import com.leetduel.leaderboard.board.LeaderboardService;
import com.leetduel.leaderboard.dto.LeaderboardTopResponse;
import com.leetduel.leaderboard.dto.RankResponse;
import com.leetduel.leaderboard.dto.RankWindowResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Public, unauthenticated (see the Gateway's public-paths entries for
// /leaderboard/top|rank|around) - rankings are treated as public data,
// same as most competitive platforms. Deliberately flat, query-param-based
// paths rather than /leaderboard/rank/{userId}: the Gateway's
// JwtAuthWebFilter allowlist is an EXACT path match with no
// wildcard/prefix support (confirmed by reading it directly - see the
// Phase 4 plan), so a path-variable design would need a filter code
// change; this design needs none.
@RestController
@RequestMapping("/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @Value("${leetduel.leaderboard.top.default-limit}")
    private int defaultLimit;

    @Value("${leetduel.leaderboard.top.max-limit}")
    private int maxLimit;

    @Value("${leetduel.leaderboard.rank-window.default-size}")
    private int defaultWindow;

    @Value("${leetduel.leaderboard.rank-window.max-size}")
    private int maxWindow;

    @GetMapping("/top")
    public ResponseEntity<LeaderboardTopResponse> top(@RequestParam Board board,
            @RequestParam(required = false) Integer limit) {
        int effectiveLimit = Math.min(limit == null ? defaultLimit : limit, maxLimit);
        return ResponseEntity.ok(leaderboardService.getTop(board, Math.max(1, effectiveLimit)));
    }

    @GetMapping("/rank")
    public ResponseEntity<RankResponse> rank(@RequestParam Board board, @RequestParam UUID userId) {
        return ResponseEntity.ok(leaderboardService.getRank(board, userId));
    }

    @GetMapping("/around")
    public ResponseEntity<RankWindowResponse> around(@RequestParam Board board, @RequestParam UUID userId,
            @RequestParam(required = false) Integer window) {
        int effectiveWindow = Math.min(window == null ? defaultWindow : window, maxWindow);
        return ResponseEntity.ok(leaderboardService.getRankWindow(board, userId, Math.max(0, effectiveWindow)));
    }
}
