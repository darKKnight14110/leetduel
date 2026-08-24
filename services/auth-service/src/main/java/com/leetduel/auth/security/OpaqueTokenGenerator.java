package com.leetduel.auth.security;

import java.security.SecureRandom;
import java.util.Base64;

// Backs both refresh tokens and email verification/reset tokens - a random
// opaque bearer value, not a JWT. Unlike the access token, nothing ever
// needs to read claims out of these without a DB round trip, so there's no
// reason to pay JWT's structure/size for them.
public final class OpaqueTokenGenerator {

    // 256 bits - matches the SHA-256 hash it'll be stored as, and is well
    // past any brute-force-relevant threshold for a value nobody ever has to
    // remember.
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private OpaqueTokenGenerator() {
    }

    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        // URL-safe, no padding - goes straight into an email link's query
        // string or a JSON response body without further escaping.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
