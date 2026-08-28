CREATE EXTENSION IF NOT EXISTS vector;
CREATE SCHEMA IF NOT EXISTS practice;

CREATE TABLE practice.problem_documents (
    problem_id UUID PRIMARY KEY,
    slug VARCHAR(120) NOT NULL,
    title VARCHAR(240) NOT NULL,
    description TEXT NOT NULL,
    difficulty VARCHAR(20) NOT NULL,
    tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    source VARCHAR(80),
    source_id VARCHAR(160),
    content_hash VARCHAR(64) NOT NULL,
    embedding vector(2048),
    embedding_model VARCHAR(160),
    embedded_at TIMESTAMPTZ,
    indexed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_practice_documents_difficulty ON practice.problem_documents (difficulty);
CREATE INDEX idx_practice_documents_source ON practice.problem_documents (source, source_id);

CREATE TABLE practice.attempts (
    submission_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    problem_id UUID NOT NULL,
    language VARCHAR(20) NOT NULL,
    verdict VARCHAR(30) NOT NULL,
    test_cases_passed INTEGER NOT NULL,
    test_cases_total INTEGER NOT NULL,
    judged_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_practice_attempts_user_judged ON practice.attempts (user_id, judged_at DESC);
CREATE INDEX idx_practice_attempts_user_problem ON practice.attempts (user_id, problem_id, judged_at DESC);

CREATE TABLE practice.progress (
    user_id UUID NOT NULL,
    problem_id UUID NOT NULL,
    attempted_count INTEGER NOT NULL DEFAULT 0,
    solved BOOLEAN NOT NULL DEFAULT false,
    first_solved_at TIMESTAMPTZ,
    last_attempt_at TIMESTAMPTZ NOT NULL,
    last_verdict VARCHAR(30) NOT NULL,
    best_passed INTEGER NOT NULL DEFAULT 0,
    best_total INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, problem_id)
);

CREATE INDEX idx_practice_progress_user_solved ON practice.progress (user_id, solved, last_attempt_at DESC);

CREATE TABLE practice.explanation_jobs (
    id UUID PRIMARY KEY,
    submission_id UUID NOT NULL UNIQUE REFERENCES practice.attempts(submission_id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    problem_id UUID NOT NULL,
    source_code TEXT NOT NULL,
    hint_status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    hint_json JSONB,
    walkthrough_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUESTED',
    walkthrough_json JSONB,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_practice_explanation_jobs_user_updated ON practice.explanation_jobs (user_id, updated_at DESC);
