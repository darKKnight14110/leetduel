package com.leetduel.auth.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, "http://localhost:8082/auth/verify-email",
                "http://localhost:3000/reset-password");
    }

    @Test
    void sendVerificationEmail_buildsLinkWithEncodedToken() {
        // Act
        emailService.sendVerificationEmail("alice@example.com", "raw token+with/chars");

        // Assert
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getTo()).containsExactly("alice@example.com");
        // URL-encoded, not the raw token verbatim - "+" and "/" aren't safe
        // unescaped in a query string.
        assertThat(message.getText()).contains("http://localhost:8082/auth/verify-email?token=raw+token%2Bwith%2Fchars");
    }

    @Test
    void sendPasswordResetEmail_buildsResetLink() {
        // Act
        emailService.sendPasswordResetEmail("alice@example.com", "raw-reset-token");

        // Assert
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getText()).contains("http://localhost:3000/reset-password?token=raw-reset-token");
    }

    @Test
    void send_doesNotPropagate_whenMailSenderThrows() {
        // Arrange - this is the resilience guarantee AuthService now relies
        // on: it no longer wraps these calls in its own try/catch (see
        // AuthService.signup/forgotPassword/resendVerification), so it has
        // to actually hold here, at the source, not just be assumed.
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        // Act & Assert
        assertThatCode(() -> emailService.sendVerificationEmail("alice@example.com", "token"))
                .doesNotThrowAnyException();
    }
}
