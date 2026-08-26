package com.leetduel.judge.verdict;

// Independently duplicated from submission-service's own Verdict enum - the
// wire contract (this string, as sent in SubmissionJudgedEvent) is the
// actual interface, not a shared class.
public enum Verdict {
    ACCEPTED,
    WRONG_ANSWER,
    TIME_LIMIT_EXCEEDED,
    RUNTIME_ERROR,
    COMPILE_ERROR,
    // Judging pipeline itself broke (daemon unreachable, overall-ceiling
    // fired) - kept distinguishable from TIME_LIMIT_EXCEEDED, which means
    // the user's own code was slow, not that judging failed.
    INTERNAL_ERROR
}
