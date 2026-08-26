package com.leetduel.submission.submission;

// Independently duplicated from Judge Worker's own Verdict enum - no
// shared lib between services, the wire contract (this string, as sent in
// SubmissionJudgedEvent) is the actual interface.
public enum Verdict {
    ACCEPTED,
    WRONG_ANSWER,
    TIME_LIMIT_EXCEEDED,
    RUNTIME_ERROR,
    COMPILE_ERROR,
    INTERNAL_ERROR
}
