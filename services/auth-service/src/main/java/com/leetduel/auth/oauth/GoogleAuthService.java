package com.leetduel.auth.oauth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.leetduel.auth.exception.InvalidGoogleTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

// Verifies the Google-issued ID token itself (signature, issuer, audience,
// expiry) server-side, rather than trusting whatever the client claims about
// who they are - the ID token is what proves the client actually completed
// Google's sign-in, not this service's word for it. GoogleIdTokenVerifier
// caches Google's public signing keys and refetches them as they rotate, so
// this doesn't hit Google's network on every call.
@Service
public class GoogleAuthService {

    private final GoogleIdTokenVerifier verifier;

    public GoogleAuthService(@Value("${google.oauth.client-id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                // Audience check is what stops an ID token minted for some
                // OTHER app (any app, not just malicious ones) from being
                // replayed against this one - Google signs tokens for
                // whichever client_id requested them.
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    public GoogleIdentity verify(String idTokenString) {
        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(idTokenString);
        } catch (GeneralSecurityException | IOException e) {
            throw new InvalidGoogleTokenException("Failed to verify Google ID token", e);
        }

        if (idToken == null) {
            throw new InvalidGoogleTokenException("Google ID token failed signature/audience/expiry check");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        Boolean emailVerified = payload.getEmailVerified();

        // Reject rather than silently treat as unverified: this service
        // only ever calls Google's identity in order to skip its own
        // email-verification flow, so an identity Google itself won't
        // vouch for isn't good enough to create/link an account against.
        if (emailVerified == null || !emailVerified) {
            throw new InvalidGoogleTokenException("Google account email is not verified");
        }

        return new GoogleIdentity(payload.getSubject(), payload.getEmail(), true);
    }
}
