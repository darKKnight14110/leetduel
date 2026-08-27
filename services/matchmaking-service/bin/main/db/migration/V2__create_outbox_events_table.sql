-- Transactional outbox, identical pattern to auth-service's and
-- submission-service's - see either for the full crash-window reasoning.
-- Here it closes the same gap for match.created dispatch: writing this row
-- in the same transaction as the matches insert means a crash right after
-- commit just leaves an unpublished row for the relay's next poll, never a
-- match nobody downstream ever learns happened.
CREATE TABLE matchmaking.outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_events_unpublished
    ON matchmaking.outbox_events (created_at)
    WHERE published_at IS NULL;
