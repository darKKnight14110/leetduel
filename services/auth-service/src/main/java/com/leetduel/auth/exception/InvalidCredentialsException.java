package com.leetduel.auth.exception;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        // Deliberately generic - never reveal whether the username or the
        // password was wrong, that leaks which usernames exist (enumeration).
        super("Invalid username or password");
    }
}
