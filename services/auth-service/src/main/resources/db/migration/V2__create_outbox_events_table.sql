-- Transactional outbox. Closes the gap left by publishing to RabbitMQ from
-- an AFTER_COMMIT listener: that approach loses the event forever if the
-- process crashes between the DB commit and the listener actually running.
-- Writing this row in the SAME transaction as the auth.users insert makes
-- the event durable the instant the user row is - a crash after commit just
-- means an unpublished outbox row sits here until the relay's next poll
-- picks it up. No event is ever silently dropped.
CREATE TABLE auth.outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Doubles as the RabbitMQ routing key at relay time - "user.created"
    -- today, but this table carries any future outbound event type from
    -- this service, not just signups.
    event_type VARCHAR(100) NOT NULL,

    -- JSONB, not fixed columns: payload shape varies per event_type, and
    -- this table's job is generic durable-outbox, not a typed record of one
    -- specific event.
    payload JSONB NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- NULL = not yet relayed. Set by the poller once RabbitMQ has accepted
    -- the publish.
    published_at TIMESTAMPTZ
);

-- Partial index: the poller's only query is "find unpublished rows, oldest
-- first". Indexing just the unpublished subset keeps the index sized to the
-- backlog, not the full (ever-growing) event history.
CREATE INDEX idx_outbox_events_unpublished
    ON auth.outbox_events (created_at)
    WHERE published_at IS NULL;
