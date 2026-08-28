package com.leetduel.judge.job;

import com.leetduel.judge.event.SubmissionJudgedEvent;
import com.leetduel.judge.runner.CompileResult;
import com.leetduel.judge.runner.JavaRunner;
import com.leetduel.judge.runner.LanguageRunner;
import com.leetduel.judge.runner.PythonRunner;
import com.leetduel.judge.runner.TestCaseOutcome;
import com.leetduel.judge.runner.TestCaseStatus;
import com.leetduel.judge.sandbox.SandboxSession;
import com.leetduel.judge.verdict.Verdict;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JudgeEngine {

    private final PythonRunner pythonRunner;
    private final JavaRunner javaRunner;

    @Value("${leetduel.sandbox.overall-ceiling-ms}")
    private long overallCeilingMs;

    public String sandboxImage(String language) {
        return selectRunner(language).sandboxImage();
    }

    public SubmissionJudgedEvent judge(JudgeJobCreatedEvent job, SandboxSession sandbox) {
        long overallStart = System.currentTimeMillis();
        LanguageRunner runner = selectRunner(job.language());
        CompileResult compileResult = runner.compile(sandbox, job);
        if (!compileResult.success()) {
            return result(job, Verdict.COMPILE_ERROR, 0, job.testCases().size(), List.of());
        }

        List<SubmissionJudgedEvent.TestCaseResultPayload> results = new ArrayList<>();
        int passed = 0;
        Verdict verdict = Verdict.ACCEPTED;
        for (JudgeJobCreatedEvent.TestCasePayload testCase : job.testCases()) {
            if (System.currentTimeMillis() - overallStart > overallCeilingMs) {
                return result(job, Verdict.INTERNAL_ERROR, passed, job.testCases().size(), results);
            }
            TestCaseOutcome outcome = runner.runTestCase(sandbox, testCase, job.timeLimitMs());
            results.add(toPayload(outcome));
            if (outcome.status() == TestCaseStatus.PASSED) {
                passed++;
            } else {
                verdict = mapVerdict(outcome.status());
                break;
            }
        }
        return result(job, verdict, passed, job.testCases().size(), results);
    }

    public SubmissionJudgedEvent internalError(JudgeJobCreatedEvent job) {
        return result(job, Verdict.INTERNAL_ERROR, 0, job.testCases().size(), List.of());
    }

    private LanguageRunner selectRunner(String language) {
        return switch (language) {
            case "PYTHON" -> pythonRunner;
            case "JAVA" -> javaRunner;
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }

    private Verdict mapVerdict(TestCaseStatus status) {
        return switch (status) {
            case WRONG_ANSWER -> Verdict.WRONG_ANSWER;
            case TIME_LIMIT_EXCEEDED -> Verdict.TIME_LIMIT_EXCEEDED;
            case RUNTIME_ERROR -> Verdict.RUNTIME_ERROR;
            case PASSED -> throw new IllegalStateException("mapVerdict should never be called for a passing outcome");
        };
    }

    private SubmissionJudgedEvent.TestCaseResultPayload toPayload(TestCaseOutcome outcome) {
        boolean passed = outcome.status() == TestCaseStatus.PASSED;
        return new SubmissionJudgedEvent.TestCaseResultPayload(
                outcome.ordinal(), outcome.status().name(), outcome.runtimeMs(),
                passed ? null : outcome.expectedOutput(), passed ? null : outcome.actualOutput());
    }

    private SubmissionJudgedEvent result(JudgeJobCreatedEvent job, Verdict verdict, int passed, int total,
            List<SubmissionJudgedEvent.TestCaseResultPayload> results) {
        return new SubmissionJudgedEvent(job.submissionId(), job.matchId(), job.userId(), verdict.name(), passed, total,
                results);
    }
}
