package com.leetduel.matchmaking.match;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

// This is matchmaking-service's OWN durable record that a match was made -
// not a shared table a future Duel Service also writes to. Per this
// project's database-per-service boundary, Duel Service (Phase 3) will own
// a separate lifecycle table and treat the match.created event this
// service publishes as its only source of truth, never reading this DB
// directly. See docs/goals.md's "who owns what" note.
@Entity
@Table(name = "matches", schema = "matchmaking")
@Getter
@Setter
@NoArgsConstructor
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_a_id", nullable = false)
    private UUID userAId;

    @Column(name = "user_b_id", nullable = false)
    private UUID userBId;

    // ELO-at-match-time, not a live lookup - frozen the moment the match is
    // made, same reasoning as goals.md's duel-flow note on why a later
    // ELO-delta consumer must use this value, not each player's live rating.
    @Column(name = "user_a_elo_at_match", nullable = false)
    private int userAEloAtMatch;

    @Column(name = "user_b_elo_at_match", nullable = false)
    private int userBEloAtMatch;

    @Column(name = "problem_id", nullable = false)
    private UUID problemId;

    @Column(name = "time_limit_ms", nullable = false)
    private int timeLimitMs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
