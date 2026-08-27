package com.leetduel.matchmaking.dto;

import java.util.UUID;

// Mirrors user-service's new InternalProfileDto response shape exactly -
// see InternalProfileController#getProfile.
public record InternalProfileResponse(UUID userId, Integer elo) {
}
