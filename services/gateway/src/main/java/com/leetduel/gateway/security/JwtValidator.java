package com.leetduel.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

// The Gateway-side mirror of auth-service's JwtService - verify only, never
// issue. Same shared secret (jwt.secret, must be kept equal across both
// services' env), same claim shapes. See JwtServiceTest on the auth-service
// side: its own comment already calls out that this is "exactly what the
// Gateway will do to verify these tokens later."
@Component
public class JwtValidator {

    private final SecretKey signingKey;

    public JwtValidator(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public record ValidatedToken(String userId, boolean emailVerified) {
    }

    // Optional, not a thrown exception the caller must catch - every caller
    // of this (JwtAuthWebFilter) treats "invalid" and "expired" and
    // "malformed" identically (401), so collapsing them to empty here
    // avoids every call site needing its own catch block for the same
    // outcome.
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
