package com.leetduel.problem.dto;

// input/expectedOutput are raw JSON text (already JSONB in Postgres) -
// passed through as-is rather than deserialized into a generic Object,
// since every consumer (frontend sample display, judge harness) wants the
// literal JSON, not a Java-side re-interpretation of it.
public record TestCaseDto(int ordinal, String input, String expectedOutput, boolean sample) {

    public TestCaseDto(int ordinal, String input, String expectedOutput) {
        this(ordinal, input, expectedOutput, false);
    }
}
