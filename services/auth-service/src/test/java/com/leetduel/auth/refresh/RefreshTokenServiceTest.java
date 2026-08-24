package com.leetduel.auth.refresh;

import com.leetduel.auth.exception.InvalidRefreshTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final long EXPIRATION_MS = 2_592_000_000L;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, EXPIRATION_MS);
    }

    @Test
    void issue_savesHashedTokenWithFutureExpiry_andReturnsRawToken() {
        // Arrange
        UUID userId = UUID.randomUUID();

        // Act
        String raw = refreshTokenService.issue(userId);

        // Assert - the raw value is never what's persisted (see TokenHasher).
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getTokenHash()).isNotEqualTo(raw);
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
        assertThat(raw).isNotBlank();
    }

    @Test
    void rotate_revokesOldTokenAndIssuesNew_whenTokenActiveAndUnexpired() {
        // Arrange
        UUID userId = UUID.randomUUID();
        RefreshToken existing = activeToken(userId);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existing));

        // Act
        RefreshTokenService.Rotated rotated = refreshTokenService.rotate("raw-token");

        // Assert
        assertThat(rotated.userId()).isEqualTo(userId);
        assertThat(existing.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class)); // old marked revoked + new one issued
        verify(refreshTokenRepository, never()).revokeAllActiveForUser(any(), any());
    }

    @Test
    void rotate_revokesAllActiveSessionsAndThrows_whenPresentedTokenAlreadyRevoked() {
        // Arrange - this is the reuse-detection path: a token that was
        // already consumed by a prior rotation is being presented again,
        // which only happens if it was copied somewhere before being used.
        UUID userId = UUID.randomUUID();
        RefreshToken alreadyRevoked = activeToken(userId);
        alreadyRevoked.setRevokedAt(Instant.now().minusSeconds(60));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(alreadyRevoked));

        // Act & Assert
        assertThatThrownBy(() -> refreshTokenService.rotate("stolen-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRepository).revokeAllActiveForUser(eq(userId), any(Instant.class));
        // The revoked token that was just rejected must not also be issued
        // a successor - reuse must dead-end, not roll forward.
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void rotate_throws_whenTokenExpired() {
        // Arrange
        RefreshToken expired = activeToken(UUID.randomUUID());
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        // Act & Assert
        assertThatThrownBy(() -> refreshTokenService.rotate("expired-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rotate_throws_whenTokenUnknown() {
        // Arrange
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> refreshTokenService.rotate("never-issued"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void revoke_marksTokenRevoked_whenActive() {
        // Arrange
        RefreshToken existing = activeToken(UUID.randomUUID());
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existing));

        // Act
        refreshTokenService.revoke("raw-token");

        // Assert
        assertThat(existing.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(existing);
    }

    @Test
    void revokeAllForUser_delegatesToRepositoryBulkUpdate() {
        // Arrange
        UUID userId = UUID.randomUUID();

        // Act
        refreshTokenService.revokeAllForUser(userId);

        // Assert
        verify(refreshTokenRepository).revokeAllActiveForUser(eq(userId), any(Instant.class));
    }

    private RefreshToken activeToken(UUID userId) {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setUserId(userId);
        token.setTokenHash("some-hash");
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        return token;
    }
}
