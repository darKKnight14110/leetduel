package com.leetduel.user.dto;

import com.leetduel.user.profile.UserProfile;

import java.util.UUID;

public record PublicIdentityResponse(UUID userId, String username) {

    public static PublicIdentityResponse from(UserProfile profile) {
        return new PublicIdentityResponse(profile.getUserId(), profile.getUsername());
    }
}
