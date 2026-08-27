package com.leetduel.wsgateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

// The THIRD independently-maintained copy of this verify-only logic, after
// auth-service (issues) -> gateway (verifies). Same shared secret, same
// claim shape. No shared Gradle module exists in this repo by design (see
// docs/goals.md's Phase 3 plan) - each service duplicating this ~25 lines
// is the established convention, not an oversight.
@Component
public class JwtValidator {

    private final SecretKey signingKey;

    public JwtValidator(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public record ValidatedToken(String userId, boolean emailVerified) {
    }

    public Optional<ValidatedToken> validate(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(signingKey).build()
                    .parseSignedClaims(token)
                    .getPayload();
            boolean emailVerified = Boolean.TRUE.equals(claims.get("emailVerified", Boolean.class));
            return Optional.of(new ValidatedToken(claims.getSubject(), emailVerified));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
