package com.leetduel.submission.submission;

public enum SubmissionStatus {
    // Queued - outbox row written, may or may not have been relayed/judged
    // yet. The only non-terminal state; see the partial index on this
    // column in V1's migration.
    PENDING,
    // Terminal - verdict/testResults are populated. No JUDGING state
    // exists: Judge Worker never publishes an "in progress" signal, only
    // the final judge.job.created -> submission.judged round trip.
    JUDGED
}
