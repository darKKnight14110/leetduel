package com.leetduel.auth.oauth;

// Provider-agnostic result of verifying a third-party identity token. If a
// second provider (GitHub, etc) is ever added, it produces the same shape -
// AuthService's linking/creation logic doesn't need to know which provider
// it came from.
public record GoogleIdentity(String subject, String email, boolean emailVerified) {
}
