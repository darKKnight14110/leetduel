package com.leetduel.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        // Username or email - AuthService.login resolves whichever this is.
        @NotBlank String identifier,
        @NotBlank String password
) {
}
