package com.leetduel.judge.job;

import com.leetduel.judge.event.SubmissionJudgedEvent;
import com.leetduel.judge.runner.CompileResult;
import com.leetduel.judge.runner.JavaRunner;
import com.leetduel.judge.runner.LanguageRunner;
import com.leetduel.judge.runner.PythonRunner;
import com.leetduel.judge.runner.TestCaseOutcome;
import com.leetduel.judge.runner.TestCaseStatus;
import com.leetduel.judge.sandbox.DockerSandboxService;
import com.leetduel.judge.verdict.Verdict;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// Orchestrates one judge job end to end: create a sandbox container,
// compile, run test cases in order with short-circuit on first failure,
// publish the COMPLETE result, and always clean up the container. This
// service is deliberately stateless - see the Phase 1 plan's "no MongoDB"
// decision - so this method's local variables are the only record of a
// judged run until the publish below.
@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeJobListener {

    private final DockerSandboxService sandboxService;
    private final PythonRunner pythonRunner;
    private final JavaRunner javaRunner;
    private final RabbitTemplate rabbitTemplate;

    @Value("${leetduel.events.judge-events-exchange}")
    private String judgeEventsExchange;

    @Value("${leetduel.events.submission-judged-routing-key}")
    private String submissionJudgedRoutingKey;

    @Value("${leetduel.sandbox.overall-ceiling-ms}")
    private long overallCeilingMs;

    @RabbitListener(queues = "${leetduel.events.judge-jobs-queue}")
    public void onJudgeJob(JudgeJobCreatedEvent job) {
        long overallStart = System.currentTimeMillis();
        LanguageRunner runner = selectRunner(job.language());
        String containerId = null;

        try {
            containerId = sandboxService.createContainer(runner.sandboxImage());

            CompileResult compileResult = runner.compile(sandboxService, containerId, job);
            if (!compileResult.success()) {
                log.debug("Compile error for submission {}: {}", job.submissionId(), compileResult.errorOutput());
                publishResult(job, Verdict.COMPILE_ERROR, 0, job.testCases().size(), List.of());
                return;
            }

            List<SubmissionJudgedEvent.TestCaseResultPayload> results = new ArrayList<>();
            int passed = 0;
            Verdict verdict = Verdict.ACCEPTED;

            for (JudgeJobCreatedEvent.TestCasePayload testCase : job.testCases()) {
                if (System.currentTimeMillis() - overallStart > overallCeilingMs) {
                    // Pathological test-suite size hanging this worker
                    // thread, not the user's code being slow - kept
                    // distinguishable from TIME_LIMIT_EXCEEDED.
                    verdict = Verdict.INTERNAL_ERROR;
                    break;
                }
                TestCaseOutcome outcome = runner.runTestCase(sandboxService, containerId, testCase, job.timeLimitMs());
                results.add(toPayload(outcome));
                if (outcome.status() == TestCaseStatus.PASSED) {
                    passed++;
                } else {
                    // Short-circuit on first failure - cheaper, matches
                    // LeetCode's real Submit button. See the Phase 1 plan
                    // for the run-all trade-off this gives up.
                    verdict = mapVerdict(outcome.status());
                    break;
                }
            }

            publishResult(job, verdict, passed, job.testCases().size(), results);
        } catch (Exception e) {
            log.error("Judging failed for submission {}", job.submissionId(), e);
            publishResult(job, Verdict.INTERNAL_ERROR, 0, job.testCases().size(), List.of());
        } finally {
            if (containerId != null) {
                sandboxService.removeContainer(containerId);
            }
        }
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

    // Failure-only fields (expectedOutput/actualOutput null on PASSED) -
    // keeps the published payload, and the JSONB test_results column it
    // lands in on submission-service's side, the same variable shape a
    // short-circuited run naturally produces.
    private SubmissionJudgedEvent.TestCaseResultPayload toPayload(TestCaseOutcome outcome) {
        boolean passed = outcome.status() == TestCaseStatus.PASSED;
        return new SubmissionJudgedEvent.TestCaseResultPayload(
                outcome.ordinal(),
                outcome.status().name(),
                outcome.runtimeMs(),
                passed ? null : outcome.expectedOutput(),
                passed ? null : outcome.actualOutput());
    }

    private void publishResult(JudgeJobCreatedEvent job, Verdict verdict, int passed, int total,
            List<SubmissionJudgedEvent.TestCaseResultPayload> results) {
        SubmissionJudgedEvent event = new SubmissionJudgedEvent(job.submissionId(), verdict.name(), passed, total, results);
        rabbitTemplate.convertAndSend(judgeEventsExchange, submissionJudgedRoutingKey, event);
    }
}
