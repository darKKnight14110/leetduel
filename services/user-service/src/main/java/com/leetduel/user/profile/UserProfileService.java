package com.leetduel.user.profile;

import com.leetduel.user.dto.InternalProfileDto;
import com.leetduel.user.exception.ProfileNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

// First service layer in user-service - added now rather than having
// InternalProfileController call UserProfileRepository directly, since
// this shape would just be recreated the moment Phase 3's match.completed
// consumer needs a real read-modify-write service for ELO deltas anyway.
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    public InternalProfileDto getProfile(UUID userId) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ProfileNotFoundException("No profile yet for user " + userId));
        return new InternalProfileDto(profile.getUserId(), profile.getElo());
    }
}
