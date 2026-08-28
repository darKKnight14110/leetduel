ALTER TABLE problem.problems
    ADD COLUMN source VARCHAR(80),
    ADD COLUMN source_id VARCHAR(160);

CREATE UNIQUE INDEX uq_problems_source_source_id
    ON problem.problems (source, source_id)
    WHERE source IS NOT NULL AND source_id IS NOT NULL;
