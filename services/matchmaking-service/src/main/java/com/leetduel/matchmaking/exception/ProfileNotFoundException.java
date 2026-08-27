package com.leetduel.matchmaking.exception;

// Distinct from UserServiceUnavailableException on purpose - a 404 here
// plausibly means signup->profile-creation propagation hasn't landed yet
// (a real eventual-consistency race against user-service's
// UserCreatedListener), which should be retryable by the caller, not
// lumped in with "user-service is actually down."
public class ProfileNotFoundException extends RuntimeException {

    public ProfileNotFoundException(String message) {
        super(message);
    }
}
