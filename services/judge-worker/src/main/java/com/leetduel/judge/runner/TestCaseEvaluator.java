package com.leetduel.judge.runner;

import com.leetduel.judge.sandbox.ExecResult;
import tools.jackson.databind.ObjectMapper;

// Shared exit-code-to-status mapping + JSON structural comparison used by
// both PythonRunner and JavaRunner - identical logic either way (the
// sandbox exec's exit code and stdout mean the same thing regardless of
// language), factored out rather than duplicated across two files.
final class TestCaseEvaluator {

    private TestCaseEvaluator() {
    }

    static TestCaseOutcome evaluate(int ordinal, ExecResult execResult, String expectedOutputJson,
            ObjectMapper objectMapper, long runtimeMs) {
        if (execResult.isTimeLimitExceeded()) {
            return new TestCaseOutcome(ordinal, TestCaseStatus.TIME_LIMIT_EXCEEDED, runtimeMs,
                    expectedOutputJson, null);
        }
        if (execResult.exitCode() == null || execResult.exitCode() != 0) {
            return new TestCaseOutcome(ordinal, TestCaseStatus.RUNTIME_ERROR, runtimeMs,
                    expectedOutputJson, execResult.stderr());
        }

        String actual = execResult.stdout().trim();
        boolean matches;
        try {
            matches = objectMapper.readTree(actual).equals(objectMapper.readTree(expectedOutputJson));
        } catch (Exception e) {
            // Malformed/empty stdout where a JSON value was expected - not a
            // sandbox failure, just a wrong answer by construction.
            matches = false;
        }

        TestCaseStatus status = matches ? TestCaseStatus.PASSED : TestCaseStatus.WRONG_ANSWER;
        return new TestCaseOutcome(ordinal, status, runtimeMs, expectedOutputJson, actual);
    }
}
