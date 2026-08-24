package com.leetduel.auth.exception;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        // Deliberately generic, same reasoning as InvalidCredentialsException
        // - "expired" vs "revoked" vs "never existed" all collapse to one
        // response so a caller can't fingerprint server-side token state.
        super("Invalid or expired refresh token");
    }
}
