package com.leetduel.user.dto;

import com.leetduel.user.profile.UserProfile;

import java.util.UUID;

// Public-facing profile view (GET /users/profile/{userId}) - distinct from
// InternalProfileDto (userId + elo only, used internally by
// matchmaking-service to resolve a caller's ELO). This one exposes the
// full W/L/D + avg-opponent-ELO stats the profile page's stats panel needs.
public record ProfileStatsResponse(
        UUID userId,
        int elo,
        int duelsWon,
        int duelsLost,
        int duelsDrawn,
        Double avgOppEloWon,
        Double avgOppEloLost,
        Double avgOppEloDrawn) {

    public static ProfileStatsResponse from(UserProfile profile) {
        return new ProfileStatsResponse(profile.getUserId(), profile.getElo(), profile.getDuelsWon(),
                profile.getDuelsLost(), profile.getDuelsDrawn(), profile.getAvgOppEloWon(),
                profile.getAvgOppEloLost(), profile.getAvgOppEloDrawn());
    }
}
