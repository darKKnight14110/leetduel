package com.leetduel.user.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_profiles", schema = "profile")
@Getter
@Setter
@NoArgsConstructor
public class UserProfile {

    // Not @GeneratedValue - this id is assigned by the caller (copied from
    // auth-service's User.id at signup), never generated locally.
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private Integer elo = 1200;

    @Column(name = "codeforces_rating")
    private Integer codeforcesRating;

    @Column(name = "leetcode_rating")
    private Integer leetcodeRating;

    @Column(name = "leetcode_total_solved")
    private Integer leetcodeTotalSolved;

    @Column(name = "duels_won", nullable = false)
    private Integer duelsWon = 0;

    @Column(name = "duels_lost", nullable = false)
    private Integer duelsLost = 0;

    @Column(name = "duels_drawn", nullable = false)
    private Integer duelsDrawn = 0;

    @Column(name = "sum_opp_elo_won", nullable = false)
    private Long sumOppEloWon = 0L;

    @Column(name = "sum_opp_elo_lost", nullable = false)
    private Long sumOppEloLost = 0L;

    @Column(name = "sum_opp_elo_drawn", nullable = false)
    private Long sumOppEloDrawn = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Derived, not persisted - this is exactly the "sum/count at read time"
    // rule from the migration, applied in code.
    public Double getAvgOppEloWon() {
        return duelsWon == 0 ? null : sumOppEloWon / (double) duelsWon;
    }

    public Double getAvgOppEloLost() {
        return duelsLost == 0 ? null : sumOppEloLost / (double) duelsLost;
    }

    public Double getAvgOppEloDrawn() {
        return duelsDrawn == 0 ? null : sumOppEloDrawn / (double) duelsDrawn;
    }
}
