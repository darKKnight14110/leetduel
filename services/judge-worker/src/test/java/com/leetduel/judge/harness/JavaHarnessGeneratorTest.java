package com.leetduel.judge.harness;

import com.leetduel.judge.job.JudgeJobCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Highest-value test in this phase per the Phase 1 plan: doesn't just assert
// on generated source text, it actually javac-compiles the generated
// Main.java alongside a real Solution.java and runs it, the same way
// JavaRunner does inside a sandbox container - just against the local JDK
// instead of Docker, since correctness of the codegen doesn't depend on
// the sandbox at all. testImplementation 'org.json:json' (build.gradle)
// exists solely so this test's classpath matches what
// docker/sandbox-java/Dockerfile vendors into the real image.
class JavaHarnessGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesWorkingHarness_forIntArrayAndIntParams_returningIntArray() throws Exception {
        JudgeJobCreatedEvent job = new JudgeJobCreatedEvent(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), null,
                "JAVA",
                "class Solution {\n"
                        + "    public int[] twoSum(int[] nums, int target) {\n"
                        + "        for (int i = 0; i < nums.length; i++) {\n"
                        + "            for (int j = i + 1; j < nums.length; j++) {\n"
                        + "                if (nums[i] + nums[j] == target) return new int[]{i, j};\n"
                        + "            }\n"
                        + "        }\n"
                        + "        return new int[0];\n"
                        + "    }\n"
                        + "}\n",
                "twoSum", "int[]",
                List.of(new JudgeJobCreatedEvent.ParameterPayload("nums", "int[]"),
                        new JudgeJobCreatedEvent.ParameterPayload("target", "int")),
                2000, 256, List.of());

        String output = compileAndRun(job, "[[2,7,11,15],9]");

        assertThat(output).isEqualTo("[0,1]");
    }

    @Test
    void generatesWorkingHarness_forStringParam_returningBoolean() throws Exception {
        JudgeJobCreatedEvent job = new JudgeJobCreatedEvent(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), null,
                "JAVA",
                "class Solution {\n"
                        + "    public boolean isPalindrome(String s) {\n"
                        + "        return s.equals(new StringBuilder(s).reverse().toString());\n"
                        + "    }\n"
                        + "}\n",
                "isPalindrome", "boolean",
                List.of(new JudgeJobCreatedEvent.ParameterPayload("s", "string")),
                2000, 256, List.of());

        String output = compileAndRun(job, "[\"racecar\"]");

        assertThat(output).isEqualTo("true");
    }

    // Two array parameters of the same element type is exactly the case
    // that would break if declare1DArray's per-parameter loop variables
    // (__arr0/__arr1, not a shared __arr) ever regressed to a shared name -
    // a real compile is a stronger check than asserting on the string.
    @Test
    void generatesWorkingHarness_forTwoArrayParams_withoutVariableNameCollision() throws Exception {
        JudgeJobCreatedEvent job = new JudgeJobCreatedEvent(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), null,
                "JAVA",
                "class Solution {\n"
                        + "    public int[] concat(int[] a, int[] b) {\n"
                        + "        int[] result = new int[a.length + b.length];\n"
                        + "        System.arraycopy(a, 0, result, 0, a.length);\n"
                        + "        System.arraycopy(b, 0, result, a.length, b.length);\n"
                        + "        return result;\n"
                        + "    }\n"
                        + "}\n",
                "concat", "int[]",
                List.of(new JudgeJobCreatedEvent.ParameterPayload("a", "int[]"),
                        new JudgeJobCreatedEvent.ParameterPayload("b", "int[]")),
                2000, 256, List.of());

        String output = compileAndRun(job, "[[1,2],[3,4]]");

        assertThat(output).isEqualTo("[1,2,3,4]");
    }

    @Test
    void generatesWorkingHarness_for2DIntArrayParam_returningInt() throws Exception {
        JudgeJobCreatedEvent job = new JudgeJobCreatedEvent(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), null,
                "JAVA",
                "class Solution {\n"
                        + "    public int sumAll(int[][] matrix) {\n"
                        + "        int sum = 0;\n"
                        + "        for (int[] row : matrix) for (int v : row) sum += v;\n"
                        + "        return sum;\n"
                        + "    }\n"
                        + "}\n",
                "sumAll", "int",
                List.of(new JudgeJobCreatedEvent.ParameterPayload("matrix", "int[][]")),
                2000, 256, List.of());

        String output = compileAndRun(job, "[[[1,2],[3,4]]]");

        assertThat(output).isEqualTo("10");
    }

    @Test
    void generate_throwsIllegalArgumentException_forUnsupportedParameterType() {
        JudgeJobCreatedEvent job = new JudgeJobCreatedEvent(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), null,
                "JAVA", "class Solution {}", "foo", "int",
                List.of(new JudgeJobCreatedEvent.ParameterPayload("node", "ListNode")),
                2000, 256, List.of());

        assertThatThrownBy(() -> JavaHarnessGenerator.generate(job))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ListNode");
    }

    @Test
    void generate_throwsIllegalArgumentException_forUnsupportedReturnType() {
        JudgeJobCreatedEvent job = new JudgeJobCreatedEvent(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), null,
                "JAVA", "class Solution {}", "foo", "TreeNode",
                List.of(),
                2000, 256, List.of());

        assertThatThrownBy(() -> JavaHarnessGenerator.generate(job))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TreeNode");
    }

    private String compileAndRun(JudgeJobCreatedEvent job, String argsJson) throws Exception {
        String mainSource = JavaHarnessGenerator.generate(job);
        Files.writeString(tempDir.resolve("Solution.java"), job.sourceCode());
        Files.writeString(tempDir.resolve("Main.java"), mainSource);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int compileExit = compiler.run(null, null, null,
                "-cp", System.getProperty("java.class.path"),
                "-d", tempDir.toString(),
                tempDir.resolve("Solution.java").toString(),
                tempDir.resolve("Main.java").toString());
        assertThat(compileExit).as("javac exit code for generated harness").isZero();

        URL[] classpath = { tempDir.toUri().toURL() };
        try (URLClassLoader loader = new URLClassLoader(classpath, getClass().getClassLoader())) {
            Class<?> mainClass = Class.forName("Main", true, loader);
            Method mainMethod = mainClass.getMethod("main", String[].class);

            PrintStream originalOut = System.out;
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            System.setOut(new PrintStream(captured, true, java.nio.charset.StandardCharsets.UTF_8));
            try {
                mainMethod.invoke(null, (Object) new String[] { argsJson });
            } finally {
                System.setOut(originalOut);
            }
            return captured.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
        }
    }
}
