-- Transactional outbox, identical pattern to every other service's - see
-- matchmaking-service's V2 migration for the full crash-window reasoning.
-- This service publishes TWO event types through it (duel.progress and
-- match.completed) rather than one; OutboxRelay branches on event_type to
-- pick the right Java DTO to deserialize before re-publishing.
CREATE TABLE duel.outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_events_unpublished
    ON duel.outbox_events (created_at)
    WHERE published_at IS NULL;
