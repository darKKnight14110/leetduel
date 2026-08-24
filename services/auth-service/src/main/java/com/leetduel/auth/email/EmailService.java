package com.leetduel.auth.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

// Plain-text SimpleMailMessage, not an HTML template engine - there's no
// frontend yet for these links to point into a styled page, so a templating
// layer (Thymeleaf email templates, etc) would be building for a UI that
// doesn't exist. Swap in when the frontend does.
@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String verificationLinkBase;
    private final String resetLinkBase;

    // Constructor injection for the @Value fields too, not field injection -
    // same pattern as JwtService elsewhere in this service. Field injection
    // needs a running Spring context (or reflection hacks) just to unit
    // test; constructor injection means EmailServiceTest can build one with
    // `new` directly.
    public EmailService(
            JavaMailSender mailSender,
            @Value("${leetduel.email.verification-link-base}") String verificationLinkBase,
            @Value("${leetduel.email.reset-link-base}") String resetLinkBase
    ) {
        this.mailSender = mailSender;
        this.verificationLinkBase = verificationLinkBase;
        this.resetLinkBase = resetLinkBase;
    }

    // @Async on both send methods (see @EnableAsync on
    // AuthServiceApplication) is what actually closes the enumeration
    // timing gap AuthService.forgotPassword/resendVerification are trying
    // for: a synchronous SMTP round trip to smtp.gmail.com is hundreds of
    // milliseconds to seconds, versus a single indexed DB lookup for the
    // "account doesn't exist" branch. Without this, that gap is a directly
    // measurable oracle for enumerating registered emails no matter how
    // identical the HTTP response is. With it, the calling thread returns
    // as soon as the method is scheduled, not after the send completes, so
    // both branches take comparable time. Uses Spring's default
    // SimpleAsyncTaskExecutor (a thread per call, no pooling/bounding) -
    // fine at this project's scale, would need a bounded pool under real
    // load.
    @Async
    public void sendVerificationEmail(String toEmail, String rawToken) {
        send(toEmail, "Verify your LeetDuel email",
                "Click to verify your email: " + buildLink(verificationLinkBase, rawToken)
                        + "\n\nThis link expires in 24 hours. If you didn't create a LeetDuel account, ignore this email.");
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String rawToken) {
        send(toEmail, "Reset your LeetDuel password",
                "Click to reset your password: " + buildLink(resetLinkBase, rawToken)
                        + "\n\nThis link expires in 1 hour. If you didn't request this, ignore this email - your password won't change.");
    }

    private void send(String toEmail, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            // Caught here, not left to Spring's default async-exception
            // logging, because this runs on a background thread now - the
            // caller in AuthService can no longer catch it across the
            // thread boundary, so this is the only place a failed send gets
            // recorded for anyone to notice.
            log.warn("Failed to send email to {}", toEmail, e);
        }
    }

    private String buildLink(String base, String rawToken) {
        return base + "?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }
}
