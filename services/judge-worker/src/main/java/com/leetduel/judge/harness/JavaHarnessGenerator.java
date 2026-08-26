package com.leetduel.judge.harness;

import com.leetduel.judge.job.JudgeJobCreatedEvent;

import java.util.List;

// Generates Main.java from a problem's function signature - the actual
// novel engineering piece of this phase. Java can't `eval` its way from
// JSON args to a typed method call the way Python can, so this
// type-directed code generator produces the parse-call-serialize glue for
// each submission's specific signature. The v1 type system this generates
// against (int, long, double, boolean, string, and their 1D arrays, plus
// int[][]) is bounded on purpose - see the Phase 1 plan for what's
// explicitly out of scope (ListNode/TreeNode/custom objects/tuples).
public final class JavaHarnessGenerator {

    private JavaHarnessGenerator() {
    }

    public static String generate(JudgeJobCreatedEvent job) {
        List<JudgeJobCreatedEvent.ParameterPayload> params = job.parameters();
        StringBuilder body = new StringBuilder();
        StringBuilder callArgs = new StringBuilder();

        for (int i = 0; i < params.size(); i++) {
            JudgeJobCreatedEvent.ParameterPayload param = params.get(i);
            body.append(declareArg(i, param.type()));
            if (i > 0) {
                callArgs.append(", ");
            }
            callArgs.append("arg").append(i);
        }

        return "import org.json.JSONArray;\n\n"
                + "public class Main {\n"
                + "    public static void main(String[] args) {\n"
                + "        JSONArray __parsed = new JSONArray(args[0]);\n"
                + indent(body.toString())
                + "        " + job.returnType() + " __result = new Solution()."
                + job.functionName() + "(" + callArgs + ");\n"
                + "        " + serializeResult(job.returnType(), "__result") + "\n"
                + "    }\n"
                + "}\n";
    }

    private static String indent(String block) {
        return block.lines().map(line -> "        " + line + "\n").reduce("", String::concat);
    }

    private static String declareArg(int index, String type) {
        String arg = "arg" + index;
        return switch (type) {
            case "int" -> "int " + arg + " = __parsed.getInt(" + index + ");\n";
            case "long" -> "long " + arg + " = __parsed.getLong(" + index + ");\n";
            case "double" -> "double " + arg + " = __parsed.getDouble(" + index + ");\n";
            case "boolean" -> "boolean " + arg + " = __parsed.getBoolean(" + index + ");\n";
            case "string" -> "String " + arg + " = __parsed.getString(" + index + ");\n";
            case "int[]" -> declare1DArray(index, "int", "getInt");
            case "long[]" -> declare1DArray(index, "long", "getLong");
            case "double[]" -> declare1DArray(index, "double", "getDouble");
            case "boolean[]" -> declare1DArray(index, "boolean", "getBoolean");
            case "string[]" -> declare1DStringArray(index);
            case "int[][]" -> declare2DIntArray(index);
            default -> throw new IllegalArgumentException(
                    "Unsupported v1 parameter type: " + type + " - see JavaHarnessGenerator's supported type list");
        };
    }

    private static String declare1DArray(int index, String elementType, String getter) {
        String arg = "arg" + index;
        return elementType + "[] " + arg + ";\n"
                + "{\n"
                + "    JSONArray __arr" + index + " = __parsed.getJSONArray(" + index + ");\n"
                + "    " + arg + " = new " + elementType + "[__arr" + index + ".length()];\n"
                + "    for (int __k = 0; __k < __arr" + index + ".length(); __k++) {\n"
                + "        " + arg + "[__k] = __arr" + index + "." + getter + "(__k);\n"
                + "    }\n"
                + "}\n";
    }

    private static String declare1DStringArray(int index) {
        String arg = "arg" + index;
        return "String[] " + arg + ";\n"
                + "{\n"
                + "    JSONArray __arr" + index + " = __parsed.getJSONArray(" + index + ");\n"
                + "    " + arg + " = new String[__arr" + index + ".length()];\n"
                + "    for (int __k = 0; __k < __arr" + index + ".length(); __k++) {\n"
                + "        " + arg + "[__k] = __arr" + index + ".getString(__k);\n"
                + "    }\n"
                + "}\n";
    }

    private static String declare2DIntArray(int index) {
        String arg = "arg" + index;
        return "int[][] " + arg + ";\n"
                + "{\n"
                + "    JSONArray __outer" + index + " = __parsed.getJSONArray(" + index + ");\n"
                + "    " + arg + " = new int[__outer" + index + ".length()][];\n"
                + "    for (int __k = 0; __k < __outer" + index + ".length(); __k++) {\n"
                + "        JSONArray __inner" + index + " = __outer" + index + ".getJSONArray(__k);\n"
                + "        int[] __row" + index + " = new int[__inner" + index + ".length()];\n"
                + "        for (int __m = 0; __m < __inner" + index + ".length(); __m++) {\n"
                + "            __row" + index + "[__m] = __inner" + index + ".getInt(__m);\n"
                + "        }\n"
                + "        " + arg + "[__k] = __row" + index + ";\n"
                + "    }\n"
                + "}\n";
    }

    private static String serializeResult(String returnType, String varName) {
        return switch (returnType) {
            case "int", "long", "double", "boolean" -> "System.out.println(String.valueOf(" + varName + "));";
            case "string" -> "System.out.println(org.json.JSONObject.quote(" + varName + "));";
            case "int[]", "long[]", "double[]", "boolean[]", "string[]", "int[][]" ->
                    "System.out.println(new JSONArray(" + varName + ").toString());";
            default -> throw new IllegalArgumentException(
                    "Unsupported v1 return type: " + returnType + " - see JavaHarnessGenerator's supported type list");
        };
    }
}
