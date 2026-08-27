package com.leetduel.leaderboard.web;

import com.leetduel.leaderboard.board.Board;
import com.leetduel.leaderboard.board.LeaderboardService;
import com.leetduel.leaderboard.dto.LeaderboardEntry;
import com.leetduel.leaderboard.dto.LeaderboardTopResponse;
import com.leetduel.leaderboard.dto.RankResponse;
import com.leetduel.leaderboard.exception.UserNotRankedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LeaderboardController.class)
class LeaderboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LeaderboardService leaderboardService;

    @Test
    void top_returnsEntries() throws Exception {
        UUID userId = UUID.randomUUID();
        when(leaderboardService.getTop(eq(Board.GLOBAL), any(Integer.class)))
                .thenReturn(new LeaderboardTopResponse(Board.GLOBAL, List.of(new LeaderboardEntry(userId, 1500, 1))));

        mockMvc.perform(get("/leaderboard/top").param("board", "GLOBAL").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$.entries[0].rank").value(1));
    }

    @Test
    void rank_returns404_whenUserNotRanked() throws Exception {
        UUID userId = UUID.randomUUID();
        when(leaderboardService.getRank(Board.GLOBAL, userId))
                .thenThrow(new UserNotRankedException("User " + userId + " is not ranked on the GLOBAL board"));

        mockMvc.perform(get("/leaderboard/rank").param("board", "GLOBAL").param("userId", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void rank_returnsRankAndScore_whenRanked() throws Exception {
        UUID userId = UUID.randomUUID();
        when(leaderboardService.getRank(Board.GLOBAL, userId))
                .thenReturn(new RankResponse(Board.GLOBAL, userId, 42, 1310));

        mockMvc.perform(get("/leaderboard/rank").param("board", "GLOBAL").param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value(42))
                .andExpect(jsonPath("$.score").value(1310));
    }
}
