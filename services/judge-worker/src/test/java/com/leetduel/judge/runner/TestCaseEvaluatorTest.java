package com.leetduel.judge.runner;

import com.leetduel.judge.sandbox.ExecResult;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

// Package-private access to TestCaseEvaluator (test lives in the same
// package on purpose) - this is the shared verdict-mapping logic both
// PythonRunner and JavaRunner delegate to, exercised here directly rather
// than through either runner so all six ExecResult shapes are covered
// without needing a live Docker daemon.
class TestCaseEvaluatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returnsTimeLimitExceeded_whenHardTimeoutFired() {
        ExecResult result = new ExecResult(null, "", "", true);

        TestCaseOutcome outcome = TestCaseEvaluator.evaluate(0, result, "[0,1]", objectMapper, 5000L);

        assertThat(outcome.status()).isEqualTo(TestCaseStatus.TIME_LIMIT_EXCEEDED);
    }

    @Test
    void returnsTimeLimitExceeded_whenInContainerTimeoutExitCode124Fired() {
        ExecResult result = new ExecResult(124L, "", "", false);

        TestCaseOutcome outcome = TestCaseEvaluator.evaluate(0, result, "[0,1]", objectMapper, 2000L);

        assertThat(outcome.status()).isEqualTo(TestCaseStatus.TIME_LIMIT_EXCEEDED);
    }

    @Test
    void returnsRuntimeError_whenExitCodeNonZero() {
        ExecResult result = new ExecResult(1L, "", "Exception in thread \"main\"", false);

        TestCaseOutcome outcome = TestCaseEvaluator.evaluate(0, result, "[0,1]", objectMapper, 10L);

        assertThat(outcome.status()).isEqualTo(TestCaseStatus.RUNTIME_ERROR);
        assertThat(outcome.actualOutput()).contains("Exception");
    }

    @Test
    void returnsPassed_whenJsonStructurallyEqual_ignoringWhitespace() {
        ExecResult result = new ExecResult(0L, "[0, 1]", "", false);

        TestCaseOutcome outcome = TestCaseEvaluator.evaluate(0, result, "[0,1]", objectMapper, 10L);

        assertThat(outcome.status()).isEqualTo(TestCaseStatus.PASSED);
    }

    @Test
    void returnsWrongAnswer_whenJsonValuesDiffer() {
        ExecResult result = new ExecResult(0L, "[1, 0]", "", false);

        TestCaseOutcome outcome = TestCaseEvaluator.evaluate(0, result, "[0,1]", objectMapper, 10L);

        assertThat(outcome.status()).isEqualTo(TestCaseStatus.WRONG_ANSWER);
        assertThat(outcome.actualOutput()).isEqualTo("[1, 0]");
    }

    @Test
    void returnsWrongAnswer_whenStdoutIsMalformedJson_ratherThanThrowing() {
        ExecResult result = new ExecResult(0L, "not json at all", "", false);

        TestCaseOutcome outcome = TestCaseEvaluator.evaluate(0, result, "[0,1]", objectMapper, 10L);

        assertThat(outcome.status()).isEqualTo(TestCaseStatus.WRONG_ANSWER);
    }
}
