package com.leetduel.user.event;

import java.util.UUID;

// Wire shape of auth-service's UserCreatedEvent. Duplicated deliberately,
// not shared via a common library - see auth-service's copy for why.
public record UserCreatedEvent(UUID userId) {
}
