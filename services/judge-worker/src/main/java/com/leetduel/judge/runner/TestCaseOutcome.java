package com.leetduel.judge.runner;

// expectedOutput/actualOutput are always populated internally - JudgeJobListener
// decides whether to null them out for PASSED cases when building the
// outgoing SubmissionJudgedEvent (failure-only fields, per the Phase 1 plan's
// Mongo-less test_results JSONB design).
public record TestCaseOutcome(int ordinal, TestCaseStatus status, long runtimeMs, String expectedOutput, String actualOutput) {
}
