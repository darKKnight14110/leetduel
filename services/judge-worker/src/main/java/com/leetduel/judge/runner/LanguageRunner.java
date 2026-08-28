package com.leetduel.judge.runner;

import com.leetduel.judge.job.JudgeJobCreatedEvent;
import com.leetduel.judge.sandbox.SandboxSession;

// One implementation per supported v1 language (PythonRunner, JavaRunner).
// The extensibility seam for future languages - adding one means a new
// LanguageRunner implementation, not touching JudgeJobListener's
// orchestration.
public interface LanguageRunner {

    String sandboxImage();

    // Writes the submitted source (+ any generated harness code) into the
    // already-running sandbox container and compiles/syntax-checks it.
    CompileResult compile(SandboxSession sandbox, JudgeJobCreatedEvent job);

    TestCaseOutcome runTestCase(SandboxSession sandbox, JudgeJobCreatedEvent.TestCasePayload testCase, int timeLimitMs);
}
