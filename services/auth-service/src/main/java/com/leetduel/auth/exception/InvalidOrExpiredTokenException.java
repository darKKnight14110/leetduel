package com.leetduel.auth.exception;

// Shared by verify-email and reset-password - both look up a row in
// auth.tokens and can fail the same three ways (never existed, already
// used, past expires_at). Callers don't get told which - same enumeration
// reasoning as InvalidCredentialsException.
public class InvalidOrExpiredTokenException extends RuntimeException {
    public InvalidOrExpiredTokenException() {
        super("Invalid or expired token");
    }
}
