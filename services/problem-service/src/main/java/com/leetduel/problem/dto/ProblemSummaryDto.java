package com.leetduel.problem.dto;

import com.leetduel.problem.problem.Difficulty;

import java.util.UUID;

public record ProblemSummaryDto(UUID id, String slug, String title, Difficulty difficulty) {
}
