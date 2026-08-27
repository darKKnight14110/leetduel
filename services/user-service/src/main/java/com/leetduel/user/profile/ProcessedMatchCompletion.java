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
@Table(name = "processed_match_completions", schema = "profile")
@Getter
@Setter
@NoArgsConstructor
public class ProcessedMatchCompletion {

    @Id
    @Column(name = "match_id")
    private UUID matchId;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    public ProcessedMatchCompletion(UUID matchId) {
        this.matchId = matchId;
    }
}
