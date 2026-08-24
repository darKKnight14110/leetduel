package com.leetduel.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "test-secret-at-least-32-bytes-long-for-hs256";
    private static final long EXPIRATION_MS = 60_000;

    private final JwtService jwtService = new JwtService(SECRET, EXPIRATION_MS);

    @Test
    void issue_returnsTokenWhoseSubjectIsUserId_notUsername() {
        // Arrange
        UUID userId = UUID.randomUUID();

        // Act
        String token = jwtService.issue(userId, true);

        // Assert - decode with the same secret, exactly what the Gateway
        // will do to verify these tokens later.
        Claims claims = parse(token);

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
    }

    @Test
    void issue_setsExpirationInTheFuture() {
        // Arrange
        UUID userId = UUID.randomUUID();

        // Act
        String token = jwtService.issue(userId, true);

        // Assert
        Claims claims = parse(token);

        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(EXPIRATION_MS);
    }

    @Test
    void issue_carriesEmailVerifiedClaim_matchingArgument() {
        // Arrange
        UUID userId = UUID.randomUUID();

        // Act
        String verifiedToken = jwtService.issue(userId, true);
        String unverifiedToken = jwtService.issue(userId, false);

        // Assert - downstream services gate on this claim without a DB
        // round trip (see JwtService.issue), so it has to actually reflect
        // the argument passed at issuance time.
        assertThat(parse(verifiedToken).get("emailVerified", Boolean.class)).isTrue();
        assertThat(parse(unverifiedToken).get("emailVerified", Boolean.class)).isFalse();
    }

    private Claims parse(String token) {
        SecretKey key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
