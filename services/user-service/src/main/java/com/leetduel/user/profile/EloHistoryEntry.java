package com.leetduel.user.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "elo_history", schema = "profile")
@Getter
@NoArgsConstructor
public class EloHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "elo_after", nullable = false)
    private int eloAfter;

    @Column(name = "elo_delta", nullable = false)
    private int eloDelta;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    public EloHistoryEntry(UUID userId, UUID matchId, int eloAfter, int eloDelta) {
        this.userId = userId;
        this.matchId = matchId;
        this.eloAfter = eloAfter;
        this.eloDelta = eloDelta;
    }
}
