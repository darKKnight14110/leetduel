-- problem schema is owned exclusively by problem-service (database-per-service,
-- same convention as auth-service's auth schema / user-service's user schema).
CREATE SCHEMA IF NOT EXISTS problem;

CREATE TABLE problem.problems (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Slug, not id, is what a future frontend URL uses - kept separate from
    -- the surrogate PK so a title edit never breaks a bookmarked/shared URL
    -- the way a title-derived slug with no stable key would.
    slug VARCHAR(80) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    difficulty VARCHAR(10) NOT NULL,

    -- Per-problem limits, not a single global constant - a graph-traversal
    -- problem legitimately needs more time than a two-pointer one. Copied
    -- into the judge job message at submission time rather than looked up
    -- again mid-judge (see submission-service's schema/outbox payload).
    time_limit_ms INTEGER NOT NULL DEFAULT 2000,
    memory_limit_mb INTEGER NOT NULL DEFAULT 256,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_problems_slug UNIQUE (slug)
);

-- Serves "browse by difficulty, newest first" - the one filter the public
-- list endpoint needs.
CREATE INDEX idx_problems_difficulty_created_at ON problem.problems (difficulty, created_at DESC);

CREATE TABLE problem.tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(40) NOT NULL,
    CONSTRAINT uq_tags_name UNIQUE (name)
);

-- Composite PK doubles as the natural uniqueness constraint (a problem can't
-- carry the same tag twice) and as the index Postgres needs anyway for
-- "tags of problem X" (leading column problem_id). The separate index on
-- tag_id serves the reverse "problems tagged X" browse direction - without
-- it, that direction would be a full scan of this join table.
CREATE TABLE problem.problem_tags (
    problem_id UUID NOT NULL REFERENCES problem.problems(id) ON DELETE CASCADE,
    tag_id UUID NOT NULL REFERENCES problem.tags(id) ON DELETE CASCADE,
    PRIMARY KEY (problem_id, tag_id)
);
CREATE INDEX idx_problem_tags_tag_id ON problem.problem_tags (tag_id);

CREATE TABLE problem.test_cases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    problem_id UUID NOT NULL REFERENCES problem.problems(id) ON DELETE CASCADE,
    ordinal INTEGER NOT NULL,

    -- JSON array of argument values in the function signature's declared
    -- parameter order (e.g. [[2,7,11,15], 9] for Two Sum's (nums, target)).
    -- Not raw stdin text - this project judges via a function-signature
    -- harness, not stdin/stdout diffing.
    input JSONB NOT NULL,
    -- Single JSON value matching the function signature's return type
    -- (e.g. [0,1]).
    expected_output JSONB NOT NULL,

    -- true = shown to the user on the problem page, included in the public
    -- detail DTO. false = judge-only, never served by GET /problems/{id}.
    -- This boolean is the entire security boundary between example and
    -- hidden test cases - the public DTO mapper filters on it, and that
    -- mapping is exactly what ProblemServiceTest asserts.
    is_sample BOOLEAN NOT NULL DEFAULT false,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Serves both "public sample cases for problem X, in order" (is_sample=true
-- filter) and "every test case for problem X, in order" (judge's internal
-- fetch, no filter) off the same composite+ordinal ordering.
CREATE INDEX idx_test_cases_problem_ordinal ON problem.test_cases (problem_id, ordinal);

-- One signature per problem: the function name + return type a submission
-- must implement. Parameters live in their own child table (below) since a
-- signature has a variable number of them.
CREATE TABLE problem.function_signatures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    problem_id UUID NOT NULL REFERENCES problem.problems(id) ON DELETE CASCADE,
    function_name VARCHAR(100) NOT NULL,
    -- Type descriptor string from the v1 bounded type system: int, long,
    -- double, boolean, string, and 1D/2D arrays of those (e.g. "int[]",
    -- "int[][]"). No ListNode/TreeNode/custom objects/tuples in v1 - an
    -- explicit, named scope cut, not an oversight.
    return_type VARCHAR(20) NOT NULL,
    CONSTRAINT uq_function_signatures_problem UNIQUE (problem_id)
);

CREATE TABLE problem.parameters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    function_signature_id UUID NOT NULL REFERENCES problem.function_signatures(id) ON DELETE CASCADE,
    ordinal INTEGER NOT NULL,
    name VARCHAR(60) NOT NULL,
    type VARCHAR(20) NOT NULL
);

-- Serves "parameters of signature X, in call order" - the exact order the
-- Java harness generator and Python driver's *args unpacking both depend on.
CREATE INDEX idx_parameters_signature_ordinal ON problem.parameters (function_signature_id, ordinal);

-- Per-language editor boilerplate (e.g. Java's "class Solution { ... }"
-- wrapper). Function name/types are shared across languages (same
-- semantics); only the surface syntax shown to the user differs, which is
-- exactly what this table isolates.
CREATE TABLE problem.language_stubs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    problem_id UUID NOT NULL REFERENCES problem.problems(id) ON DELETE CASCADE,
    language VARCHAR(20) NOT NULL,
    stub_code TEXT NOT NULL,
    CONSTRAINT uq_language_stubs_problem_language UNIQUE (problem_id, language)
);
