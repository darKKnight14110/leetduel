-- Transactional outbox, identical pattern to auth-service's - see that
-- service's V2 migration for the full crash-window reasoning. Here it
-- closes the same gap for judge job dispatch: writing this row in the same
-- transaction as the submissions insert means a crash right after commit
-- just leaves an unpublished row for the relay's next poll, never a lost job.
CREATE TABLE submission.outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_events_unpublished
    ON submission.outbox_events (created_at)
    WHERE published_at IS NULL;
