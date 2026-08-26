-- submission schema is owned exclusively by submission-service
-- (database-per-service, same convention as every other schema in this repo).
CREATE SCHEMA IF NOT EXISTS submission;

CREATE TABLE submission.submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Copied from the Gateway's X-User-Id header at request time, not a
    -- cross-service FK - database-per-service means this service never
    -- joins against auth's users table directly.
    user_id UUID NOT NULL,
    -- Likewise copied from the request, not an FK into problem-service's DB.
    problem_id UUID NOT NULL,

    language VARCHAR(20) NOT NULL,

    -- Canonical, current copy of the submitted code - needed synchronously
    -- for the polling endpoint to echo it back. Submissions are immutable
    -- once created, so this can never drift from the copy Judge Worker
    -- actually ran.
    source_code TEXT NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    verdict VARCHAR(30),
    test_cases_passed INTEGER,
    test_cases_total INTEGER,

    -- Variable-shape per-test-case breakdown (array length varies by
    -- short-circuit point, failure-only fields only present on failed
    -- cases) - exactly what JSONB is for. No separate results table or
    -- second datastore; see the Phase 1 plan's "why no MongoDB" note.
    test_results JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    judged_at TIMESTAMPTZ
);

-- Serves "my submission history, newest first".
CREATE INDEX idx_submissions_user_created_at ON submission.submissions (user_id, created_at DESC);

-- Partial index on the one non-terminal status - serves a future
-- reconciliation job ("find submissions stuck in PENDING past N minutes").
-- Same partial-index-on-live-subset pattern as auth's outbox_events table.
CREATE INDEX idx_submissions_pending ON submission.submissions (created_at)
    WHERE status = 'PENDING';
