package com.leetduel.auth.user;

import com.leetduel.auth.email.EmailService;
import com.leetduel.auth.exception.EmailAlreadyExistsException;
import com.leetduel.auth.exception.InvalidCredentialsException;
import com.leetduel.auth.exception.InvalidOrExpiredTokenException;
import com.leetduel.auth.exception.UsernameAlreadyExistsException;
import com.leetduel.auth.oauth.GoogleAuthService;
import com.leetduel.auth.oauth.GoogleIdentity;
import com.leetduel.auth.oauth.OAuthIdentity;
import com.leetduel.auth.oauth.OAuthIdentityRepository;
import com.leetduel.auth.outbox.OutboxEvent;
import com.leetduel.auth.outbox.OutboxEventRepository;
import com.leetduel.auth.refresh.RefreshTokenService;
import com.leetduel.auth.security.JwtService;
import com.leetduel.auth.security.TokenHasher;
import com.leetduel.auth.verification.TokenType;
import com.leetduel.auth.verification.VerificationToken;
import com.leetduel.auth.verification.VerificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private OAuthIdentityRepository oauthIdentityRepository;
    @Mock
    private VerificationTokenRepository verificationTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private EmailService emailService;
    @Mock
    private GoogleAuthService googleAuthService;

    // Real instance, not mocked - same reasoning as before: no external
    // dependency worth mocking for a two-field record.
    private final ObjectMapper objectMapper = new ObjectMapper();

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, outboxEventRepository, oauthIdentityRepository,
                verificationTokenRepository, passwordEncoder, jwtService, refreshTokenService, emailService,
                googleAuthService, objectMapper);
    }

    @Test
    void signup_savesHashedPasswordAndReturnsTokenPair_whenUsernameAndEmailAvailable() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(userId);
            return saved;
        });
        when(jwtService.issue(userId, false)).thenReturn("jwt-token");
        when(refreshTokenService.issue(userId)).thenReturn("refresh-token");

        // Act
        AuthService.AuthResult result = authService.signup("alice", "Alice@Example.com", "password123");

        // Assert
        assertThat(result.accessToken()).isEqualTo("jwt-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("alice");
        // Lowercased before it ever reaches the entity - see User.setEmail.
        assertThat(captor.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed-password");
        assertThat(captor.getValue().isEmailVerified()).isFalse();
    }

    @Test
    void signup_throwsUsernameAlreadyExists_whenUsernameTaken() {
        // Arrange
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> authService.signup("alice", "alice@example.com", "password123"))
                .isInstanceOf(UsernameAlreadyExistsException.class);
        verify(userRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void signup_throwsEmailAlreadyExists_whenEmailTaken() {
        // Arrange
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> authService.signup("alice", "alice@example.com", "password123"))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void signup_writesOutboxEventAndVerificationTokenInSameCall_whenAvailable() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(userId);
            return saved;
        });
        when(jwtService.issue(userId, false)).thenReturn("jwt-token");
        when(refreshTokenService.issue(userId)).thenReturn("refresh-token");

        // Act
        authService.signup("alice", "alice@example.com", "password123");

        // Assert - transactional outbox, same reasoning as before: the
        // event row commits with the user row regardless of what happens to
        // email delivery afterward.
        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("user.created");
        assertThat(outboxCaptor.getValue().getPayload()).contains(userId.toString());

        ArgumentCaptor<VerificationToken> tokenCaptor = ArgumentCaptor.forClass(VerificationToken.class);
        verify(verificationTokenRepository).save(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getUserId()).isEqualTo(userId);
        assertThat(tokenCaptor.getValue().getType()).isEqualTo(TokenType.EMAIL_VERIFICATION);
        verify(emailService).sendVerificationEmail(eq("alice@example.com"), anyString());
    }

    @Test
    void login_returnsTokenPair_whenLoggingInByUsername() {
        // Arrange
        UUID userId = UUID.randomUUID();
        User user = localUser(userId, "alice", "alice@example.com", "hashed-password");
        when(userRepository.findByUsernameOrEmail("alice", "alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(true);
        when(jwtService.issue(userId, false)).thenReturn("jwt-token");
        when(refreshTokenService.issue(userId)).thenReturn("refresh-token");

        // Act
        AuthService.AuthResult result = authService.login("alice", "password123");

        // Assert
        assertThat(result.accessToken()).isEqualTo("jwt-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void login_returnsTokenPair_whenLoggingInByEmail() {
        // Arrange
        UUID userId = UUID.randomUUID();
        User user = localUser(userId, "alice", "alice@example.com", "hashed-password");
        when(userRepository.findByUsernameOrEmail("Alice@Example.com", "alice@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(true);
        when(jwtService.issue(userId, false)).thenReturn("jwt-token");
        when(refreshTokenService.issue(userId)).thenReturn("refresh-token");

        // Act
        AuthService.AuthResult result = authService.login("Alice@Example.com", "password123");

        // Assert
        assertThat(result.accessToken()).isEqualTo("jwt-token");
    }

    @Test
    void login_throwsInvalidCredentials_whenUserNotFound() {
        // Arrange
        when(userRepository.findByUsernameOrEmail("ghost", "ghost")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.login("ghost", "password123"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_throwsInvalidCredentials_whenPasswordWrong() {
        // Arrange
        User user = localUser(UUID.randomUUID(), "alice", "alice@example.com", "hashed-password");
        when(userRepository.findByUsernameOrEmail("alice", "alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed-password")).thenReturn(false);

        // Act & Assert - same exception as "user not found", deliberately.
        assertThatThrownBy(() -> authService.login("alice", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_throwsInvalidCredentials_whenAccountIsGoogleOnly() {
        // Arrange - passwordHash is null for a Google-only account; same
        // exception as a wrong password, so the response can't be used to
        // fingerprint account type.
        User googleOnlyUser = new User();
        googleOnlyUser.setId(UUID.randomUUID());
        googleOnlyUser.setUsername("alice");
        googleOnlyUser.setEmail("alice@example.com");
        when(userRepository.findByUsernameOrEmail("alice", "alice")).thenReturn(Optional.of(googleOnlyUser));

        // Act & Assert
        assertThatThrownBy(() -> authService.login("alice", "anything"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void verifyEmail_marksUserVerifiedAndTokenUsed_whenTokenValid() {
        // Arrange
        UUID userId = UUID.randomUUID();
        String rawToken = "raw-verify-token";
        VerificationToken token = verificationToken(userId, TokenType.EMAIL_VERIFICATION, Instant.now().plusSeconds(3600));
        when(verificationTokenRepository.findByTokenHashAndType(TokenHasher.sha256Hex(rawToken), TokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(token));
        User user = localUser(userId, "alice", "alice@example.com", "hashed-password");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        authService.verifyEmail(rawToken);

        // Assert
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(token.getUsedAt()).isNotNull();
    }

    @Test
    void verifyEmail_throws_whenTokenAlreadyUsed() {
        // Arrange
        UUID userId = UUID.randomUUID();
        VerificationToken token = verificationToken(userId, TokenType.EMAIL_VERIFICATION, Instant.now().plusSeconds(3600));
        token.setUsedAt(Instant.now().minusSeconds(60));
        when(verificationTokenRepository.findByTokenHashAndType(anyString(), eq(TokenType.EMAIL_VERIFICATION)))
                .thenReturn(Optional.of(token));

        // Act & Assert - a captured/forwarded link can't be replayed.
        assertThatThrownBy(() -> authService.verifyEmail("raw-token"))
                .isInstanceOf(InvalidOrExpiredTokenException.class);
    }

    @Test
    void verifyEmail_throws_whenTokenExpired() {
        // Arrange
        UUID userId = UUID.randomUUID();
        VerificationToken token = verificationToken(userId, TokenType.EMAIL_VERIFICATION, Instant.now().minusSeconds(1));
        when(verificationTokenRepository.findByTokenHashAndType(anyString(), eq(TokenType.EMAIL_VERIFICATION)))
                .thenReturn(Optional.of(token));

        // Act & Assert
        assertThatThrownBy(() -> authService.verifyEmail("raw-token"))
                .isInstanceOf(InvalidOrExpiredTokenException.class);
    }

    @Test
    void resetPassword_updatesPasswordAndRevokesAllSessions() {
        // Arrange - the security-critical assertion: every existing refresh
        // token dies the moment a password is reset, not just the one used
        // in this request.
        UUID userId = UUID.randomUUID();
        String rawToken = "raw-reset-token";
        VerificationToken token = verificationToken(userId, TokenType.PASSWORD_RESET, Instant.now().plusSeconds(3600));
        when(verificationTokenRepository.findByTokenHashAndType(TokenHasher.sha256Hex(rawToken), TokenType.PASSWORD_RESET))
                .thenReturn(Optional.of(token));
        User user = localUser(userId, "alice", "alice@example.com", "old-hash");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword123")).thenReturn("new-hash");

        // Act
        authService.resetPassword(rawToken, "newPassword123");

        // Assert
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(token.getUsedAt()).isNotNull();
        verify(refreshTokenService).revokeAllForUser(userId);
    }

    @Test
    void googleLogin_createsNewAccountAndLinksIdentity_whenNoExistingUserOrLink() {
        // Arrange
        String subject = "google-sub-123";
        when(googleAuthService.verify("id-token")).thenReturn(new GoogleIdentity(subject, "new@example.com", true));
        when(oauthIdentityRepository.findByProviderAndProviderUserId("GOOGLE", subject)).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        UUID userId = UUID.randomUUID();
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(userId);
            return saved;
        });
        when(jwtService.issue(userId, true)).thenReturn("jwt-token");
        when(refreshTokenService.issue(userId)).thenReturn("refresh-token");

        // Act
        AuthService.AuthResult result = authService.googleLogin("id-token");

        // Assert
        assertThat(result.accessToken()).isEqualTo("jwt-token");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        // Google already verified this email - the account starts verified,
        // no separate email-verification-link flow needed for it.
        assertThat(userCaptor.getValue().isEmailVerified()).isTrue();
        assertThat(userCaptor.getValue().getPasswordHash()).isNull();
        verify(outboxEventRepository).save(any(OutboxEvent.class));
        ArgumentCaptor<OAuthIdentity> linkCaptor = ArgumentCaptor.forClass(OAuthIdentity.class);
        verify(oauthIdentityRepository).save(linkCaptor.capture());
        assertThat(linkCaptor.getValue().getProviderUserId()).isEqualTo(subject);
    }

    @Test
    void googleLogin_linksToExistingLocalAccount_whenEmailMatchesAndAlreadyVerified() {
        // Arrange - safe to auto-link: BOTH Google and this app's own
        // verification flow have independently proven control of this
        // email address.
        String subject = "google-sub-456";
        when(googleAuthService.verify("id-token")).thenReturn(new GoogleIdentity(subject, "alice@example.com", true));
        when(oauthIdentityRepository.findByProviderAndProviderUserId("GOOGLE", subject)).thenReturn(Optional.empty());
        UUID userId = UUID.randomUUID();
        User existing = localUser(userId, "alice", "alice@example.com", "hashed-password");
        existing.setEmailVerified(true);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(existing));
        when(jwtService.issue(userId, true)).thenReturn("jwt-token");
        when(refreshTokenService.issue(userId)).thenReturn("refresh-token");

        // Act
        authService.googleLogin("id-token");

        // Assert - no user row is created or modified for a link to an
        // already-verified account, just the new OAuthIdentity row.
        verify(userRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
        verify(oauthIdentityRepository).save(any(OAuthIdentity.class));
    }

    @Test
    void googleLogin_throwsEmailAlreadyExists_whenLocalAccountEmailMatchesButNeverVerified() {
        // Arrange - the account pre-hijacking case: an attacker could have
        // signed up locally with the victim's real email, left it
        // unverified, and be waiting for the victim's first Google sign-in
        // to hand over a session on the attacker's own passwordHash.
        // Google's verification proves the email address, not who created
        // this particular row with it - only this app's OWN verification
        // (proof someone controlled the inbox through its flow) makes
        // auto-linking safe.
        String subject = "google-sub-999";
        when(googleAuthService.verify("id-token")).thenReturn(new GoogleIdentity(subject, "victim@example.com", true));
        when(oauthIdentityRepository.findByProviderAndProviderUserId("GOOGLE", subject)).thenReturn(Optional.empty());
        User unverifiedExisting = localUser(UUID.randomUUID(), "attacker-planted", "victim@example.com", "attacker-hash");
        unverifiedExisting.setEmailVerified(false);
        when(userRepository.findByEmail("victim@example.com")).thenReturn(Optional.of(unverifiedExisting));

        // Act & Assert
        assertThatThrownBy(() -> authService.googleLogin("id-token"))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verify(userRepository, never()).save(any());
        verify(oauthIdentityRepository, never()).save(any());
        verify(jwtService, never()).issue(any(), anyBoolean());
    }

    @Test
    void googleLogin_logsInDirectly_whenIdentityAlreadyLinked() {
        // Arrange
        String subject = "google-sub-789";
        UUID userId = UUID.randomUUID();
        when(googleAuthService.verify("id-token")).thenReturn(new GoogleIdentity(subject, "alice@example.com", true));
        OAuthIdentity link = new OAuthIdentity();
        link.setUserId(userId);
        when(oauthIdentityRepository.findByProviderAndProviderUserId("GOOGLE", subject)).thenReturn(Optional.of(link));
        User existing = localUser(userId, "alice", "alice@example.com", "hashed-password");
        // Already verified from whenever this link was first created (see
        // the other two googleLogin tests) - this branch trusts that state
        // rather than re-deriving it from the identity on every login.
        existing.setEmailVerified(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));
        when(jwtService.issue(userId, true)).thenReturn("jwt-token");
        when(refreshTokenService.issue(userId)).thenReturn("refresh-token");

        // Act
        AuthService.AuthResult result = authService.googleLogin("id-token");

        // Assert - no new user, no new link, just a token pair for the
        // already-linked account.
        assertThat(result.accessToken()).isEqualTo("jwt-token");
        verify(userRepository, never()).save(any());
        verify(oauthIdentityRepository, never()).save(any());
    }

    private User localUser(UUID id, String username, String email, String passwordHash) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        return user;
    }

    private VerificationToken verificationToken(UUID userId, TokenType type, Instant expiresAt) {
        VerificationToken token = new VerificationToken();
        token.setId(UUID.randomUUID());
        token.setUserId(userId);
        token.setType(type);
        token.setExpiresAt(expiresAt);
        return token;
    }
}
