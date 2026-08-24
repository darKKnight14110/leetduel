package com.leetduel.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

// SHA-256, not bcrypt - shared by VerificationToken and RefreshToken hashing.
// Both hash a 256-bit SecureRandom value, never a human-chosen secret, so
// there's no low-entropy input for a slow KDF to protect against; a fast
// hash is what these call sites need (refresh happens on every token
// rotation, verification on every clicked link).
public final class TokenHasher {

    private TokenHasher() {
    }

    public static String sha256Hex(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JDK - this can't
            // actually happen, but the checked exception has to go somewhere.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
