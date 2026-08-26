package com.leetduel.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtValidatorTest {

    private static final String SECRET = "test-secret-at-least-32-bytes-long-for-hs256";
    private final JwtValidator jwtValidator = new JwtValidator(SECRET);

    @Test
    void validate_returnsUserIdAndEmailVerified_whenTokenSignedWithSameSecret() {
        // Arrange - mirrors exactly what auth-service's JwtService.issue
        // produces, since this test's whole point is confirming the two
        // services agree on token shape.
        UUID userId = UUID.randomUUID();
        String token = issue(userId, true);

        // Act
        Optional<JwtValidator.ValidatedToken> result = jwtValidator.validate(token);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().userId()).isEqualTo(userId.toString());
        assertThat(result.get().emailVerified()).isTrue();
    }

    @Test
    void validate_returnsEmpty_whenSignedWithDifferentSecret() {
        // Arrange - the actual security property under test: a token this
        // Gateway didn't co-sign (wrong secret) must never be trusted,
        // regardless of how well-formed its claims look.
        String token = issue("different-secret-at-least-32-bytes-long", UUID.randomUUID(), true);

        // Act & Assert
        assertThat(jwtValidator.validate(token)).isEmpty();
    }

    @Test
    void validate_returnsEmpty_whenTokenExpired() {
        // Arrange
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant past = Instant.now().minusSeconds(3600);
        String expiredToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("emailVerified", true)
                .issuedAt(Date.from(past))
                .expiration(Date.from(past.plusSeconds(60)))
                .signWith(key)
                .compact();

        // Act & Assert
        assertThat(jwtValidator.validate(expiredToken)).isEmpty();
    }

    @Test
    void validate_returnsEmpty_whenTokenMalformed() {
        assertThat(jwtValidator.validate("not-a-real-jwt")).isEmpty();
    }

    private String issue(UUID userId, boolean emailVerified) {
        return issue(SECRET, userId, emailVerified);
    }

    private String issue(String secret, UUID userId, boolean emailVerified) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("emailVerified", emailVerified)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(key)
                .compact();
    }
}
