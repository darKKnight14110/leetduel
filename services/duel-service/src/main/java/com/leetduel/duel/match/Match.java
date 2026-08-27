package com.leetduel.duel.match;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

// This service's own lifecycle record - keyed by the SAME id as
// matchmaking-service's own matches table (the matchId carried in
// match.created), never a freshly generated UUID. Database-per-service:
// this service never reads matchmaking-service's Postgres directly, only
// its published events.
@Entity
@Table(name = "matches", schema = "duel")
@Getter
@Setter
@NoArgsConstructor
public class Match {

    @Id
    private UUID id;

    @Column(name = "player1_id", nullable = false)
    private UUID player1Id;

    @Column(name = "player2_id", nullable = false)
    private UUID player2Id;

    @Column(name = "player1_elo_at_match", nullable = false)
    private int player1EloAtMatch;

    @Column(name = "player2_elo_at_match", nullable = false)
    private int player2EloAtMatch;

    @Column(name = "problem_id", nullable = false)
    private UUID problemId;

    @Column(name = "time_limit_ms", nullable = false)
    private int timeLimitMs;

    @Column(name = "player1_progress_pct", nullable = false)
    private int player1ProgressPct = 0;

    @Column(name = "player2_progress_pct", nullable = false)
    private int player2ProgressPct = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchStatus status = MatchStatus.IN_PROGRESS;

    @Column(name = "winner_id")
    private UUID winnerId;

    @Column(name = "is_draw", nullable = false)
    private boolean draw = false;

    @Column(name = "player1_elo_delta")
    private Integer player1EloDelta;

    @Column(name = "player2_elo_delta")
    private Integer player2EloDelta;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    // Optimistic-lock CAS guard: two near-simultaneous writes to the same
    // match (both players hitting 100% at once, or a timeout sweep firing
    // exactly as the last submission lands) must not both win the
    // status=IN_PROGRESS -> COMPLETED transition. Whichever transaction
    // commits second gets an ObjectOptimisticLockingFailureException,
    // caught by the caller and treated as a no-op - the other transaction
    // already completed the match and published match.completed.
    @Version
    private Long version;

    public boolean isPlayer1(UUID userId) {
        return player1Id.equals(userId);
    }
}
