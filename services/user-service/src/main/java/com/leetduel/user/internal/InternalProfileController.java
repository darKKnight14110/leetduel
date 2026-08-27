package com.leetduel.user.internal;

import com.leetduel.user.dto.InternalProfileDto;
import com.leetduel.user.profile.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Not routed through the Gateway at all (no leetduel.gateway.routes entry
// covers /internal/**) - unreachable from outside this service's own
// network, which is the entire access control here. Called directly by
// matchmaking-service at queue-join time to resolve the caller's current
// ELO, exact same pattern as problem-service's InternalProblemController.
@RestController
@RequestMapping("/internal/profiles")
@RequiredArgsConstructor
public class InternalProfileController {

    private final UserProfileService userProfileService;

    @GetMapping("/{userId}")
    public ResponseEntity<InternalProfileDto> getProfile(@PathVariable UUID userId) {
        return ResponseEntity.ok(userProfileService.getProfile(userId));
    }
}
