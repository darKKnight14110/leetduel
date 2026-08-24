package com.leetduel.auth.refresh;

import com.leetduel.auth.exception.InvalidRefreshTokenException;
import com.leetduel.auth.security.OpaqueTokenGenerator;
import com.leetduel.auth.security.TokenHasher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final long expirationMs;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${jwt.refresh-expiration-ms}") long expirationMs
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.expirationMs = expirationMs;
    }

    public String issue(UUID userId) {
        String raw = OpaqueTokenGenerator.generate();
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(TokenHasher.sha256Hex(raw));
        token.setExpiresAt(Instant.now().plusMillis(expirationMs));
        refreshTokenRepository.save(token);
        return raw;
    }

    public record Rotated(String rawRefreshToken, UUID userId) {
    }

    // Rotation: every refresh consumes the presented token and issues a
    // fresh one, rather than letting one refresh token live untouched for
    // its whole 30-day window. That single behavior is also the
    // reuse-detection mechanism - see the revoked-token branch below - so
    // rotation isn't just hygiene, it's what makes theft detectable at all.
    @Transactional
    public Rotated rotate(String rawRefreshToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (token.getRevokedAt() != null) {
            // This exact token was already rotated away once. The only way
            // it can be presented again is if it got copied somewhere
            // before being consumed (stolen, or a client bug that replays
            // an old value) - either way, the current chain is no longer
            // trustworthy, so every active token for this user is burned,
            // not just this one. The legitimate client, holding the token
            // this one was rotated INTO, simply gets logged out too and has
            // to sign in again - an acceptable cost for closing the window.
            log.warn("Refresh token reuse detected for user {}, revoking all active sessions", token.getUserId());
            refreshTokenRepository.revokeAllActiveForUser(token.getUserId(), Instant.now());
            throw new InvalidRefreshTokenException();
        }

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }

        token.setRevokedAt(Instant.now());
        refreshTokenRepository.save(token);

        return new Rotated(issue(token.getUserId()), token.getUserId());
    }

    public void revoke(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawRefreshToken))
                .filter(t -> t.getRevokedAt() == null)
                .ifPresent(t -> {
                    t.setRevokedAt(Instant.now());
                    refreshTokenRepository.save(t);
                });
    }

    // Called on password reset - a changed password should kill every
    // existing session, not just require the new password on next login.
    public void revokeAllForUser(UUID userId) {
        refreshTokenRepository.revokeAllActiveForUser(userId, Instant.now());
    }
}
