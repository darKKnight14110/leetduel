package com.leetduel.user.profile;

import com.leetduel.user.dto.PublicIdentityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class PublicIdentityController {

    private static final int MAX_IDENTITIES = 100;

    private final UserProfileService userProfileService;

    @GetMapping("/public-identities")
    public ResponseEntity<List<PublicIdentityResponse>> getIdentities(@RequestParam List<UUID> ids) {
        List<UUID> requestedIds = new LinkedHashSet<>(ids).stream().toList();
        if (requestedIds.isEmpty() || requestedIds.size() > MAX_IDENTITIES) {
            return ResponseEntity.badRequest().build();
        }

        List<PublicIdentityResponse> identities = userProfileService.getProfiles(requestedIds).stream()
                .filter(profile -> profile.getUsername() != null)
                .map(PublicIdentityResponse::from)
                .toList();
        return ResponseEntity.ok(identities);
    }
}
