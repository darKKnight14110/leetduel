package com.leetduel.judge.runner;

import com.leetduel.judge.job.JudgeJobCreatedEvent;
import com.leetduel.judge.sandbox.DockerSandboxService;

// One implementation per supported v1 language (PythonRunner, JavaRunner).
// The extensibility seam for future languages - adding one means a new
// LanguageRunner implementation, not touching JudgeJobListener's
// orchestration.
public interface LanguageRunner {

    String sandboxImage();

    // Writes the submitted source (+ any generated harness code) into the
    // already-running sandbox container and compiles/syntax-checks it.
    CompileResult compile(DockerSandboxService sandbox, String containerId, JudgeJobCreatedEvent job);

    TestCaseOutcome runTestCase(DockerSandboxService sandbox, String containerId,
            JudgeJobCreatedEvent.TestCasePayload testCase, int timeLimitMs);
}
