package com.leetduel.auth.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHasherTest {

    @Test
    void sha256Hex_isDeterministic_forSameInput() {
        assertThat(TokenHasher.sha256Hex("abc")).isEqualTo(TokenHasher.sha256Hex("abc"));
    }

    @Test
    void sha256Hex_differsForDifferentInput() {
        assertThat(TokenHasher.sha256Hex("abc")).isNotEqualTo(TokenHasher.sha256Hex("abd"));
    }

    @Test
    void sha256Hex_producesSixtyFourLowercaseHexChars() {
        // 64 hex chars = 256 bits - matches the VARCHAR(64) token_hash
        // columns in the V4/V6 migrations.
        String hash = TokenHasher.sha256Hex("any-opaque-token-value");

        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }
}
