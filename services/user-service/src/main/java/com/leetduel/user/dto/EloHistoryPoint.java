package com.leetduel.user.dto;

import com.leetduel.user.profile.EloHistoryEntry;

import java.time.Instant;
import java.util.UUID;

public record EloHistoryPoint(UUID matchId, int eloAfter, int eloDelta, Instant recordedAt) {

    public static EloHistoryPoint from(EloHistoryEntry entry) {
        return new EloHistoryPoint(entry.getMatchId(), entry.getEloAfter(), entry.getEloDelta(), entry.getRecordedAt());
    }
}
