package com.leetduel.user.profile;

import com.leetduel.user.dto.EloHistoryPoint;
import com.leetduel.user.dto.ProfileStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Genuinely new public surface (Phase 4) - unlike InternalProfileController,
// there was no reachable-from-outside profile endpoint at all before this.
// Routed through the Gateway's existing /users route, JWT required (not
// added to public-paths) - consistent with duel-service's /duels/{matchId}
// also being read-only but authenticated.
@RestController
@RequestMapping("/users/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserProfileService userProfileService;

    @GetMapping("/{userId}")
    public ResponseEntity<ProfileStatsResponse> getStats(@PathVariable UUID userId) {
        return ResponseEntity.ok(ProfileStatsResponse.from(userProfileService.getProfileOrThrow(userId)));
    }

    @GetMapping("/{userId}/history")
    public ResponseEntity<List<EloHistoryPoint>> getHistory(@PathVariable UUID userId) {
        List<EloHistoryPoint> points = userProfileService.getEloHistory(userId).stream()
                .map(EloHistoryPoint::from)
                .toList();
        return ResponseEntity.ok(points);
    }
}
