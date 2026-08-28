package com.leetduel.judge.runner;

// expectedOutput/actualOutput are retained internally so the evaluator can
// compare the complete result. JudgeEngine omits them for passed cases, and
// Submission Service redacts hidden values before persistence.
public record TestCaseOutcome(int ordinal, TestCaseStatus status, long runtimeMs, String expectedOutput, String actualOutput,
        boolean sample) {

    public TestCaseOutcome(int ordinal, TestCaseStatus status, long runtimeMs, String expectedOutput, String actualOutput) {
        this(ordinal, status, runtimeMs, expectedOutput, actualOutput, false);
    }
}
