package com.leetduel.judge.runner;

public record CompileResult(boolean success, String errorOutput) {

    public static CompileResult ok() {
        return new CompileResult(true, null);
    }

    public static CompileResult failed(String errorOutput) {
        return new CompileResult(false, errorOutput);
    }
}
