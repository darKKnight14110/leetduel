package com.leetduel.judge.job;

import com.leetduel.judge.event.SubmissionJudgedEvent;
import com.leetduel.judge.runner.CompileResult;
import com.leetduel.judge.runner.JavaRunner;
import com.leetduel.judge.runner.PythonRunner;
import com.leetduel.judge.runner.TestCaseOutcome;
import com.leetduel.judge.runner.TestCaseStatus;
import com.leetduel.judge.sandbox.DockerSandboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Mirrors UserCreatedListenerTest/SubmissionServiceTest's style: manual
// Mockito wiring, no Spring context, no real Docker daemon. @Value fields
// aren't part of JudgeJobListener's @RequiredArgsConstructor (only the four
// collaborator beans are), so they're set via ReflectionTestUtils the same
// way a @Value field would be populated by Spring at runtime.
@ExtendWith(MockitoExtension.class)
class JudgeJobListenerTest {

    @Mock
    private DockerSandboxService sandboxService;
    @Mock
    private PythonRunner pythonRunner;
    @Mock
    private JavaRunner javaRunner;
    @Mock
    private RabbitTemplate rabbitTemplate;

    private JudgeJobListener listener;

    @BeforeEach
    void setUp() {
        listener = new JudgeJobListener(sandboxService, pythonRunner, javaRunner, rabbitTemplate);
        ReflectionTestUtils.setField(listener, "judgeEventsExchange", "judge.events");
        ReflectionTestUtils.setField(listener, "submissionJudgedRoutingKey", "submission.judged");
        ReflectionTestUtils.setField(listener, "overallCeilingMs", 30000L);
    }

    private JudgeJobCreatedEvent jobWith(int testCaseCount) {
        List<JudgeJobCreatedEvent.TestCasePayload> cases = java.util.stream.IntStream.range(0, testCaseCount)
                .mapToObj(i -> new JudgeJobCreatedEvent.TestCasePayload(i, "[1]", "[1]"))
                .toList();
        return new JudgeJobCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "PYTHON", "def f(x): return x", "f", "int[]",
                List.of(new JudgeJobCreatedEvent.ParameterPayload("x", "int[]")),
                2000, 256, cases);
    }

    @Test
    void publishesCompileError_andRemovesContainer_whenCompileFails() {
        JudgeJobCreatedEvent job = jobWith(3);
        when(sandboxService.createContainer(any())).thenReturn("container-1");
        when(pythonRunner.compile(sandboxService, "container-1", job))
                .thenReturn(CompileResult.failed("SyntaxError"));

        listener.onJudgeJob(job);

        ArgumentCaptor<SubmissionJudgedEvent> captor = ArgumentCaptor.forClass(SubmissionJudgedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("judge.events"), eq("submission.judged"), captor.capture());
        SubmissionJudgedEvent published = captor.getValue();
        assertThat(published.verdict()).isEqualTo("COMPILE_ERROR");
        assertThat(published.testCasesPassed()).isZero();
        assertThat(published.testCasesTotal()).isEqualTo(3);
        assertThat(published.testResults()).isEmpty();
        verify(pythonRunner, never()).runTestCase(any(), any(), any(), anyInt());
        verify(sandboxService).removeContainer("container-1");
    }

    @Test
    void publishesAccepted_whenAllTestCasesPass() {
        JudgeJobCreatedEvent job = jobWith(2);
        when(sandboxService.createContainer(any())).thenReturn("container-2");
        when(pythonRunner.compile(sandboxService, "container-2", job)).thenReturn(CompileResult.ok());
        when(pythonRunner.runTestCase(eq(sandboxService), eq("container-2"), any(), eq(2000)))
                .thenReturn(new TestCaseOutcome(0, TestCaseStatus.PASSED, 5L, "[1]", "[1]"))
                .thenReturn(new TestCaseOutcome(1, TestCaseStatus.PASSED, 6L, "[1]", "[1]"));

        listener.onJudgeJob(job);

        ArgumentCaptor<SubmissionJudgedEvent> captor = ArgumentCaptor.forClass(SubmissionJudgedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("judge.events"), eq("submission.judged"), captor.capture());
        SubmissionJudgedEvent published = captor.getValue();
        assertThat(published.verdict()).isEqualTo("ACCEPTED");
        assertThat(published.testCasesPassed()).isEqualTo(2);
        assertThat(published.testCasesTotal()).isEqualTo(2);
        // Failure-only fields stay null on PASSED cases - see
        // JudgeJobListener.toPayload's comment on why.
        assertThat(published.testResults()).allSatisfy(r -> {
            assertThat(r.expectedOutput()).isNull();
            assertThat(r.actualOutput()).isNull();
        });
        verify(sandboxService).removeContainer("container-2");
    }

    @Test
    void shortCircuitsOnFirstFailure_andMapsWrongAnswerVerdict() {
        JudgeJobCreatedEvent job = jobWith(3);
        when(sandboxService.createContainer(any())).thenReturn("container-3");
        when(pythonRunner.compile(sandboxService, "container-3", job)).thenReturn(CompileResult.ok());
        when(pythonRunner.runTestCase(eq(sandboxService), eq("container-3"), any(), eq(2000)))
                .thenReturn(new TestCaseOutcome(0, TestCaseStatus.PASSED, 5L, "[1]", "[1]"))
                .thenReturn(new TestCaseOutcome(1, TestCaseStatus.WRONG_ANSWER, 5L, "[1]", "[2]"));

        listener.onJudgeJob(job);

        ArgumentCaptor<SubmissionJudgedEvent> captor = ArgumentCaptor.forClass(SubmissionJudgedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("judge.events"), eq("submission.judged"), captor.capture());
        SubmissionJudgedEvent published = captor.getValue();
        assertThat(published.verdict()).isEqualTo("WRONG_ANSWER");
        assertThat(published.testCasesPassed()).isEqualTo(1);
        assertThat(published.testCasesTotal()).isEqualTo(3);
        // Short-circuit: only 2 results published even though the job had 3
        // test cases - the third was never run.
        assertThat(published.testResults()).hasSize(2);
        verify(pythonRunner, times(2)).runTestCase(any(), any(), any(), anyInt());
    }

    @Test
    void publishesInternalError_andStillRemovesContainer_whenAnExceptionEscapes() {
        JudgeJobCreatedEvent job = jobWith(1);
        when(sandboxService.createContainer(any())).thenReturn("container-4");
        when(pythonRunner.compile(sandboxService, "container-4", job))
                .thenThrow(new RuntimeException("docker daemon unreachable"));

        listener.onJudgeJob(job);

        ArgumentCaptor<SubmissionJudgedEvent> captor = ArgumentCaptor.forClass(SubmissionJudgedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("judge.events"), eq("submission.judged"), captor.capture());
        assertThat(captor.getValue().verdict()).isEqualTo("INTERNAL_ERROR");
        verify(sandboxService).removeContainer("container-4");
    }

    @Test
    void publishesInternalError_whenOverallCeilingExceededMidRun() {
        JudgeJobCreatedEvent job = jobWith(2);
        ReflectionTestUtils.setField(listener, "overallCeilingMs", 0L);
        when(sandboxService.createContainer(any())).thenReturn("container-5");
        when(pythonRunner.compile(sandboxService, "container-5", job)).thenReturn(CompileResult.ok());

        listener.onJudgeJob(job);

        ArgumentCaptor<SubmissionJudgedEvent> captor = ArgumentCaptor.forClass(SubmissionJudgedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("judge.events"), eq("submission.judged"), captor.capture());
        assertThat(captor.getValue().verdict()).isEqualTo("INTERNAL_ERROR");
        // Ceiling fired before the loop ever executed a test case - distinct
        // from a slow user submission (TIME_LIMIT_EXCEEDED).
        verify(pythonRunner, never()).runTestCase(any(), any(), any(), anyInt());
    }
}
