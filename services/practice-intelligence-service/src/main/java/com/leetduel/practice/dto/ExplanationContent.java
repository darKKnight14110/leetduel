package com.leetduel.practice.dto;

import java.util.List;

public record ExplanationContent(
        String summary,
        String whatHappened,
        List<String> concepts,
        String hint,
        String complexity,
        List<String> nextSteps,
        String walkthrough
) {
}
