package com.leetduel.judge.runner;

import com.leetduel.judge.job.JudgeJobCreatedEvent;
import com.leetduel.judge.sandbox.ExecResult;
import com.leetduel.judge.sandbox.SandboxSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Component
public class PythonRunner implements LanguageRunner {

    // Not a test case, not the problem's own time limit - a small fixed
    // budget for the syntax check itself.
    private static final int COMPILE_TIMEOUT_MS = 5000;

    private final String pythonImage;
    private final ObjectMapper objectMapper;

    public PythonRunner(@Value("${leetduel.sandbox.python-image}") String pythonImage, ObjectMapper objectMapper) {
        this.pythonImage = pythonImage;
        this.objectMapper = objectMapper;
    }

    @Override
    public String sandboxImage() {
        return pythonImage;
    }

    // Python has no real compile phase - a py_compile syntax check keeps
    // the interface symmetric with JavaRunner and catches obvious errors
    // before spending a test-case exec, without pretending Python compiles
    // the way Java does.
    @Override
    public CompileResult compile(SandboxSession sandbox, JudgeJobCreatedEvent job) {
        String driver = "import json, sys\n"
                + "sys.path.insert(0, '/sandbox')\n"
                + "from solution import " + job.functionName() + " as fn\n"
                + "args = json.loads(sys.argv[1])\n"
                + "print(json.dumps(fn(*args)))\n";

        sandbox.copyFiles(Map.of(
                "solution.py", job.sourceCode(),
                "driver.py", driver));

        ExecResult result = sandbox.exec(List.of("python", "-m", "py_compile", "/sandbox/solution.py"),
                COMPILE_TIMEOUT_MS);
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
                List.of("python", "/sandbox/driver.py", testCase.input()), timeLimitMs);
        long runtimeMs = System.currentTimeMillis() - start;
        return TestCaseEvaluator.evaluate(testCase.ordinal(), result, testCase.expectedOutput(), objectMapper, runtimeMs);
    }
}
