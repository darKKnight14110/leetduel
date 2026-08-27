package com.leetduel.auth.user;

import com.leetduel.auth.email.EmailService;
import com.leetduel.auth.event.UserCreatedEvent;
import com.leetduel.auth.exception.EmailAlreadyExistsException;
import com.leetduel.auth.exception.InvalidCredentialsException;
import com.leetduel.auth.exception.InvalidOrExpiredTokenException;
import com.leetduel.auth.exception.InvalidRefreshTokenException;
import com.leetduel.auth.exception.UsernameAlreadyExistsException;
import com.leetduel.auth.outbox.OutboxEvent;
import com.leetduel.auth.outbox.OutboxEventRepository;
import com.leetduel.auth.refresh.RefreshTokenService;
import com.leetduel.auth.security.JwtService;
import com.leetduel.auth.security.OpaqueTokenGenerator;
import com.leetduel.auth.security.TokenHasher;
import com.leetduel.auth.verification.TokenType;
import com.leetduel.auth.verification.VerificationToken;
import com.leetduel.auth.verification.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    // 24h to verify, 1h to reset - reset is the more sensitive of the two
    // (a live reset link is a path straight to account takeover), so it
    // gets a tighter window. Not yet externalized to application.properties
    // like jwt.expiration-ms is - these aren't environment-specific the way
    // a secret or a per-deploy tuning knob is, so a config property would
    // just be indirection without a reason to vary it.
    private static final Duration EMAIL_VERIFICATION_TTL = Duration.ofHours(24);
    private static final Duration PASSWORD_RESET_TTL = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    public record AuthResult(String accessToken, String refreshToken) {
    }

    @Transactional
    @SneakyThrows
    public AuthResult signup(String username, String email, String rawPassword) {
        String normalizedEmail = email.toLowerCase(Locale.ROOT);

        // Same DB-unique-constraint-is-the-real-guard reasoning as the
        // username check below - these two calls just turn the common
        // non-racing case into a friendly 409 instead of a raw
        // constraint-violation exception.
        if (userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException(username);
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user = userRepository.save(user);

        writeUserCreatedOutboxEvent(user.getId(), user.getUsername());

        String rawVerificationToken = issueVerificationToken(user.getId(), TokenType.EMAIL_VERIFICATION, EMAIL_VERIFICATION_TTL);
        // @Async (see EmailService) - the send happens on a background
        // thread, so a slow or failed SMTP call can't roll back the
        // already-committed signup and can't be caught here.
        emailService.sendVerificationEmail(user.getEmail(), rawVerificationToken);

        return issueTokenPair(user);
    }

    public AuthResult login(String identifier, String rawPassword) {
        User user = userRepository.findByUsernameOrEmail(identifier, identifier.toLowerCase(Locale.ROOT))
                .orElseThrow(InvalidCredentialsException::new);

        // Null-guard, not a dead branch: passwordHash stays nullable at the
        // DB level (see User.passwordHash) even though every current signup
        // path always sets it - failing closed here rather than trusting
        // that invariant is cheap insurance against a future write path
        // that forgets to set it.
        if (user.getPasswordHash() == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return issueTokenPair(user);
    }

    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        RefreshTokenService.Rotated rotated = refreshTokenService.rotate(rawRefreshToken);
        // Re-read the user row rather than trusting stale claims - this is
        // the point where a mid-session email verification (or a future
        // "role" claim) catches up, per JwtService.issue's staleness note.
        User user = userRepository.findById(rotated.userId()).orElseThrow(InvalidRefreshTokenException::new);
        String accessToken = jwtService.issue(user.getId(), user.isEmailVerified());
        return new AuthResult(accessToken, rotated.rawRefreshToken());
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        VerificationToken token = requireValidToken(rawToken, TokenType.EMAIL_VERIFICATION);

        User user = userRepository.findById(token.getUserId()).orElseThrow(InvalidOrExpiredTokenException::new);
        user.setEmailVerified(true);
        userRepository.save(user);

        markTokenUsed(token);
    }

    // Always succeeds from the caller's point of view regardless of whether
    // the email exists or is already verified - same enumeration reasoning
    // as forgotPassword. The controller returns one generic "if that email
    // needs verifying, we sent a link" response either way.
    @Transactional
    public void resendVerification(String email) {
        userRepository.findByEmail(email.toLowerCase(Locale.ROOT))
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> {
                    verificationTokenRepository.invalidateAllPending(user.getId(), TokenType.EMAIL_VERIFICATION, Instant.now());
                    String rawToken = issueVerificationToken(user.getId(), TokenType.EMAIL_VERIFICATION, EMAIL_VERIFICATION_TTL);
                    emailService.sendVerificationEmail(user.getEmail(), rawToken);
                });
    }

    // Deliberately silent and identically-timed-feeling for "no such email"
    // and "email exists" - the classic user-enumeration hole in password
    // reset flows is a response (status, body, or timing) that differs
    // based on account existence.
    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email.toLowerCase(Locale.ROOT))
                .filter(user -> user.getPasswordHash() != null)
                .ifPresent(user -> {
                    verificationTokenRepository.invalidateAllPending(user.getId(), TokenType.PASSWORD_RESET, Instant.now());
                    String rawToken = issueVerificationToken(user.getId(), TokenType.PASSWORD_RESET, PASSWORD_RESET_TTL);
                    emailService.sendPasswordResetEmail(user.getEmail(), rawToken);
                });
    }

    @Transactional
    public void resetPassword(String rawToken, String newRawPassword) {
        VerificationToken token = requireValidToken(rawToken, TokenType.PASSWORD_RESET);

        User user = userRepository.findById(token.getUserId()).orElseThrow(InvalidOrExpiredTokenException::new);
        user.setPasswordHash(passwordEncoder.encode(newRawPassword));
        userRepository.save(user);

        markTokenUsed(token);

        // The security-critical step: a password reset implies the old
        // password (and anyone who had it) should no longer have access -
        // every existing refresh token, everywhere, dies here rather than
        // waiting out its 30-day expiry.
        refreshTokenService.revokeAllForUser(user.getId());
    }

    private AuthResult issueTokenPair(User user) {
        String accessToken = jwtService.issue(user.getId(), user.isEmailVerified());
        String refreshToken = refreshTokenService.issue(user.getId());
        return new AuthResult(accessToken, refreshToken);
    }

    private String issueVerificationToken(UUID userId, TokenType type, Duration ttl) {
        String raw = OpaqueTokenGenerator.generate();
        VerificationToken token = new VerificationToken();
        token.setUserId(userId);
        token.setTokenHash(TokenHasher.sha256Hex(raw));
        token.setType(type);
        token.setExpiresAt(Instant.now().plus(ttl));
        verificationTokenRepository.save(token);
        return raw;
    }

    private VerificationToken requireValidToken(String rawToken, TokenType type) {
        VerificationToken token = verificationTokenRepository
                .findByTokenHashAndType(TokenHasher.sha256Hex(rawToken), type)
                .orElseThrow(InvalidOrExpiredTokenException::new);

        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidOrExpiredTokenException();
        }
        return token;
    }

    private void markTokenUsed(VerificationToken token) {
        token.setUsedAt(Instant.now());
        verificationTokenRepository.save(token);
    }

    private void writeUserCreatedOutboxEvent(UUID userId, String username) throws Exception {
        // Written in the SAME transaction as the user row (transactional
        // outbox) - see OutboxRelay for the full reasoning: user-service
        // needs a profile row created for every new account, and this is
        // the one place account creation happens.
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setEventType("user.created");
        outboxEvent.setPayload(objectMapper.writeValueAsString(new UserCreatedEvent(userId, username)));
        outboxEventRepository.save(outboxEvent);
    }
}
