package com.leetduel.matchmaking.queue;

import com.leetduel.matchmaking.dto.QueueStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Every route here requires a JWT at the Gateway (no public-paths entry
// for /matchmaking). X-User-Id is injected by the Gateway's
// JwtAuthWebFilter after JWT verification - this controller trusts it
// rather than re-verifying the token itself, same as every other
// downstream service in this repo.
@RestController
@RequestMapping("/matchmaking/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    @PostMapping("/join")
    public ResponseEntity<Void> join(@RequestHeader("X-User-Id") UUID userId) {
        queueService.join(userId);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/status")
    public ResponseEntity<QueueStatusResponse> status(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(queueService.getStatus(userId));
    }

    // 200 + body, not 204 - unlike a hard resource delete, leave has an
    // inherent race with the pairing sweep the client needs visibility
    // into: a bare 204 would let a client believe cancellation succeeded
    // when it actually just got matched a moment earlier.
    @DeleteMapping("/leave")
    public ResponseEntity<QueueStatusResponse> leave(@RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(queueService.leave(userId));
    }
}
