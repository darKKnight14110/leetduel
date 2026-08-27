package com.leetduel.duel.event;

import java.util.UUID;

// Published via the outbox to match.events, routing key duel.progress -
// fired on every non-terminal progress update. WS Gateway (Phase 3) is the
// only real consumer: it relays this raw payload to /topic/duel/{matchId}
// so the OTHER player's browser can render a progress bar. Deliberately
// carries only a percentage, never code or test-case detail - preserves
// competitive integrity (an opponent should never see WHY you're stuck) and
// keeps the payload trivially small for a high-frequency event.
public record DuelProgressEvent(
        UUID matchId,
        UUID userId,
        int progressPct) {
}
