package com.leetduel.auth.dto;

import jakarta.validation.constraints.NotBlank;

// idToken comes from Google Identity Services running client-side (frontend
// completes the Google sign-in popup/redirect and hands this service only
// the resulting ID token) - this backend never sees the user's Google
// password or session, only a signed assertion of who they are.
public record GoogleLoginRequest(@NotBlank String idToken) {
}
