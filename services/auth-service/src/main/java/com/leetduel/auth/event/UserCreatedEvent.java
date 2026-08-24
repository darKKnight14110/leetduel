package com.leetduel.auth.event;

import java.util.UUID;

// Message body published to RabbitMQ on the "user.events" exchange, routing
// key "user.created". Deliberately duplicated (not shared-lib'd) in
// user-service's consumer - two independently deployable services should
// not share a compile-time dependency just for a message shape; the wire
// contract (JSON field names) is the actual interface between them.
public record UserCreatedEvent(UUID userId) {
}
