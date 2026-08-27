package com.leetduel.user.dto;

import java.util.UUID;

public record InternalProfileDto(UUID userId, Integer elo) {
}
