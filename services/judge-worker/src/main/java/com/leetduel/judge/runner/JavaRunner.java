package com.leetduel.judge.runner;

import com.leetduel.judge.harness.JavaHarnessGenerator;
import com.leetduel.judge.job.JudgeJobCreatedEvent;
import com.leetduel.judge.sandbox.ExecResult;
import com.leetduel.judge.sandbox.SandboxSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Component
public class JavaRunner implements LanguageRunner {

    private static final int COMPILE_TIMEOUT_MS = 10000;

    private final String javaImage;
    private final String jsonLibPath;
    private final ObjectMapper objectMapper;

    public JavaRunner(
            @Value("${leetduel.sandbox.java-image}") String javaImage,
            @Value("${leetduel.sandbox.json-lib-path}") String jsonLibPath,
            ObjectMapper objectMapper) {
        this.javaImage = javaImage;
        this.jsonLibPath = jsonLibPath;
        this.objectMapper = objectMapper;
    }

    @Override
    public String sandboxImage() {
        return javaImage;
    }

    // Solution.java is the user's submitted class body, assumed to match
    // the "class Solution { ... }" stub they were shown. Main.java is
    // GENERATED per submission from the problem's function signature - see
    // JavaHarnessGenerator, the actual novel piece of this phase. Both
    // files compile together since Main references Solution directly.
    @Override
    public CompileResult compile(SandboxSession sandbox, JudgeJobCreatedEvent job) {
        String mainSource = JavaHarnessGenerator.generate(job);

        sandbox.copyFiles(Map.of(
                "Solution.java", job.sourceCode(),
                "Main.java", mainSource));

        ExecResult result = sandbox.exec(
                List.of("javac", "-cp", jsonLibPath, "Solution.java", "Main.java"), COMPILE_TIMEOUT_MS);
        if (result.exitCode() == null || result.exitCode() != 0) {
            return CompileResult.failed(result.stderr());
        }
        return CompileResult.ok();
    }

    @Override
    public TestCaseOutcome runTestCase(SandboxSession sandbox,
            JudgeJobCreatedEvent.TestCasePayload testCase, int timeLimitMs) {
        long start = System.currentTimeMillis();
        ExecResult result = sandbox.exec(
                List.of("java", "-cp", ".:" + jsonLibPath, "Main", testCase.input()), timeLimitMs);
        long runtimeMs = System.currentTimeMillis() - start;
        return TestCaseEvaluator.evaluate(testCase.ordinal(), result, testCase.expectedOutput(), objectMapper, runtimeMs);
    }
}
