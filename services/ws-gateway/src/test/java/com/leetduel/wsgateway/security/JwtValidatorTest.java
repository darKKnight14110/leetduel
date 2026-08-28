package com.leetduel.wsgateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtValidatorTest {

    private static final String SECRET = "test-secret-at-least-32-bytes-long-for-hs256";
    private final JwtValidator validator = new JwtValidator(SECRET);

    @Test
    void validate_returnsUserIdentityForSignedToken() {
        String token = issue("user-123", true);

        assertThat(validator.validate(token))
                .hasValue(new JwtValidator.ValidatedToken("user-123", true));
    }

    @Test
    void validate_returnsEmptyForTokenWithoutSubject() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .claim("emailVerified", true)
                .issuedAt(Date.from(Instant.now()))
                .signWith(key)
                .compact();

        assertThat(validator.validate(token)).isEmpty();
    }

    @Test
    void validate_returnsEmptyForMalformedToken() {
        assertThat(validator.validate("not-a-real-jwt")).isEmpty();
    }

    private String issue(String userId, boolean emailVerified) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .claim("emailVerified", emailVerified)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(key)
                .compact();
    }
}
