package com.leetduel.duel.match;

import com.leetduel.duel.exception.MatchNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Routed through the Gateway (JWT required, no public-paths entry) - the
// frontend's initial page-load / reconnect-recovery path. WS carries only
// live deltas after this; a client that opens the page slightly after
// match.created broadcast, or reconnects mid-match, isn't left blind
// waiting for the next duel.progress tick.
@RestController
@RequestMapping("/duels")
@RequiredArgsConstructor
public class DuelController {

    private final MatchRepository matchRepository;

    @GetMapping("/{matchId}")
    public ResponseEntity<MatchResponse> getById(@PathVariable UUID matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new MatchNotFoundException("Match not found: " + matchId));
        return ResponseEntity.ok(MatchResponse.from(match));
    }
}
