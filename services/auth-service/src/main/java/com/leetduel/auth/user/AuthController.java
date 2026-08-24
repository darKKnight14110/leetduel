package com.leetduel.auth.user;

import com.leetduel.auth.dto.AuthResponse;
import com.leetduel.auth.dto.ForgotPasswordRequest;
import com.leetduel.auth.dto.GoogleLoginRequest;
import com.leetduel.auth.dto.LoginRequest;
import com.leetduel.auth.dto.LogoutRequest;
import com.leetduel.auth.dto.MessageResponse;
import com.leetduel.auth.dto.RefreshRequest;
import com.leetduel.auth.dto.ResendVerificationRequest;
import com.leetduel.auth.dto.ResetPasswordRequest;
import com.leetduel.auth.dto.SignupRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        AuthService.AuthResult result = authService.signup(request.username(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.AuthResult result = authService.login(request.identifier(), request.password());
        return ResponseEntity.ok(toResponse(result));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthService.AuthResult result = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(toResponse(result));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    // GET, not POST - this is meant to be followed directly from an emailed
    // link (a browser click, no client-side request body to attach).
    @GetMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@RequestParam @NotBlank String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(new MessageResponse("Email verified"));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.email());
        // Same generic body regardless of whether the email exists or was
        // already verified - see AuthService.resendVerification.
        return ResponseEntity.ok(new MessageResponse("If that email needs verifying, a link has been sent"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        // Same generic body regardless of account existence - see
        // AuthService.forgotPassword for the enumeration reasoning.
        return ResponseEntity.ok(new MessageResponse("If that email has an account, a reset link has been sent"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(new MessageResponse("Password reset"));
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        AuthService.AuthResult result = authService.googleLogin(request.idToken());
        return ResponseEntity.ok(toResponse(result));
    }

    private AuthResponse toResponse(AuthService.AuthResult result) {
        return new AuthResponse(result.accessToken(), result.refreshToken());
    }
}
