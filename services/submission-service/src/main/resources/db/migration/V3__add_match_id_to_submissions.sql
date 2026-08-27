-- Nullable: absent for practice-mode submissions, set for submissions made
-- from a live duel (Phase 3). Lets Duel Service's judge.events consumer
-- distinguish "this verdict belongs to a match" from "just practice" without
-- a separate topic/queue - see docs/goals.md's Phase 3 duel flow.
ALTER TABLE submission.submissions ADD COLUMN match_id UUID;

-- Serves Duel Service-adjacent debugging/reconciliation: "all submissions
-- for this match". Partial index since most rows (practice mode) never
-- populate this column.
CREATE INDEX idx_submissions_match_id ON submission.submissions (match_id)
    WHERE match_id IS NOT NULL;
