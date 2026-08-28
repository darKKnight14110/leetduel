package com.leetduel.judge.job;

import com.leetduel.judge.event.SubmissionJudgedEvent;
import com.leetduel.judge.runner.CompileResult;
import com.leetduel.judge.runner.JavaRunner;
import com.leetduel.judge.runner.PythonRunner;
import com.leetduel.judge.runner.TestCaseOutcome;
import com.leetduel.judge.runner.TestCaseStatus;
import com.leetduel.judge.sandbox.DockerSandboxService;
import com.leetduel.judge.sandbox.SandboxSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

@ExtendWith(MockitoExtension.class)
class JudgeJobListenerTest {

    @Mock
    private DockerSandboxService sandboxService;
    @Mock
    private SandboxSession sandbox;
    @Mock
    private PythonRunner pythonRunner;
    @Mock
    private JavaRunner javaRunner;
    @Mock
    private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    private JudgeEngine engine;
    private JudgeJobListener listener;

    @BeforeEach
    void setUp() {
        engine = new JudgeEngine(pythonRunner, javaRunner);
        ReflectionTestUtils.setField(engine, "overallCeilingMs", 30000L);
        listener = new JudgeJobListener(sandboxService, engine, rabbitTemplate);
        ReflectionTestUtils.setField(listener, "judgeEventsExchange", "judge.events");
        ReflectionTestUtils.setField(listener, "submissionJudgedRoutingKey", "submission.judged");
    }

    private JudgeJobCreatedEvent jobWith(int testCaseCount) {
        List<JudgeJobCreatedEvent.TestCasePayload> cases = java.util.stream.IntStream.range(0, testCaseCount)
                .mapToObj(index -> new JudgeJobCreatedEvent.TestCasePayload(index, "[1]", "[1]"))
                .toList();
        return new JudgeJobCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "PYTHON", "def f(x): return x", "f", "int[]",
                List.of(new JudgeJobCreatedEvent.ParameterPayload("x", "int[]")),
                2000, 256, cases);
    }

    @Test
    void publishesCompileErrorAndClosesDockerSession() {
        JudgeJobCreatedEvent job = jobWith(3);
        when(pythonRunner.sandboxImage()).thenReturn("python-image");
        when(sandboxService.open("python-image")).thenReturn(sandbox);
        when(pythonRunner.compile(sandbox, job)).thenReturn(CompileResult.failed("SyntaxError"));

        listener.onJudgeJob(job);

        SubmissionJudgedEvent published = publishedResult();
        assertThat(published.verdict()).isEqualTo("COMPILE_ERROR");
        assertThat(published.testCasesPassed()).isZero();
        assertThat(published.testCasesTotal()).isEqualTo(3);
        assertThat(published.testResults()).isEmpty();
        verify(pythonRunner, never()).runTestCase(any(), any(), anyInt());
        verify(sandbox).close();
    }

    @Test
    void shortCircuitsOnFirstFailureAndMapsWrongAnswer() {
        JudgeJobCreatedEvent job = jobWith(3);
        when(pythonRunner.sandboxImage()).thenReturn("python-image");
        when(sandboxService.open("python-image")).thenReturn(sandbox);
        when(pythonRunner.compile(sandbox, job)).thenReturn(CompileResult.ok());
        when(pythonRunner.runTestCase(eq(sandbox), any(), eq(2000)))
                .thenReturn(new TestCaseOutcome(0, TestCaseStatus.PASSED, 5L, "[1]", "[1]"))
                .thenReturn(new TestCaseOutcome(1, TestCaseStatus.WRONG_ANSWER, 5L, "[1]", "[2]"));

        listener.onJudgeJob(job);

        SubmissionJudgedEvent published = publishedResult();
        assertThat(published.verdict()).isEqualTo("WRONG_ANSWER");
        assertThat(published.testCasesPassed()).isEqualTo(1);
        assertThat(published.testResults()).hasSize(2);
        verify(pythonRunner, times(2)).runTestCase(any(), any(), anyInt());
        verify(sandbox).close();
    }

    @Test
    void publishesInternalErrorWhenDockerSessionFails() {
        JudgeJobCreatedEvent job = jobWith(1);
        when(pythonRunner.sandboxImage()).thenReturn("python-image");
        when(sandboxService.open("python-image")).thenThrow(new RuntimeException("daemon unavailable"));

        listener.onJudgeJob(job);

        assertThat(publishedResult().verdict()).isEqualTo("INTERNAL_ERROR");
    }

    private SubmissionJudgedEvent publishedResult() {
        ArgumentCaptor<SubmissionJudgedEvent> captor = ArgumentCaptor.forClass(SubmissionJudgedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("judge.events"), eq("submission.judged"), captor.capture());
        return captor.getValue();
    }
}
