package com.leetduel.auth.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    // Subject is the user's id, not their username - so a future username
    // change doesn't invalidate already-issued tokens, and the Gateway never
    // has to look username up to identify the caller.
    //
    // emailVerified rides along as a claim rather than requiring a DB
    // lookup on every request that cares about it - the trade-off is
    // staleness: verifying mid-session doesn't update an already-issued
    // access token until it's refreshed. Access tokens are short-lived
    // (see jwt.expiration-ms), so that window is bounded, and
    // RefreshTokenService.rotate re-reads the user row on every refresh -
    // see AuthService.refresh.
    public String issue(UUID userId, boolean emailVerified) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("emailVerified", emailVerified)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(signingKey)
                .compact();
    }
}
