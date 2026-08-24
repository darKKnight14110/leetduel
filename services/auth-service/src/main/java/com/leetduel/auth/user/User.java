package com.leetduel.auth.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "users", schema = "auth")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String username;

    // Setter excluded from Lombok - normalization below has to run on every
    // write, and a Lombok-generated setter would give every other call site
    // a second, un-normalized way to set this field.
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    // Nullable: a Google-only signup never has a local password. "does this
    // account have a usable password" is an application-level check
    // (passwordHash != null), not something the DB enforces here - see
    // V3 migration for why a DB CHECK can't express the full invariant.
    @Column(name = "password_hash")
    private String passwordHash;

    // True immediately for a Google signup (Google already verified the
    // email); false until the verify-email link is clicked for a local
    // signup. Mirrored into the JWT as a claim by JwtService so downstream
    // services don't need a DB round trip to gate on it.
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    // Set by Hibernate at insert time, not left to the column's DB-side
    // DEFAULT - Hibernate sends an explicit value for every mapped field on
    // insert (NULL if unset), which would otherwise override the DB default
    // and violate the NOT NULL constraint.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Lowercased so "Foo@gmail.com" and "foo@gmail.com" can never both
    // register - the DB's UNIQUE constraint only sees byte-equal strings, it
    // doesn't case-fold, so normalization has to happen before the value
    // ever reaches it.
    public void setEmail(String email) {
        this.email = email == null ? null : email.toLowerCase(Locale.ROOT);
    }
}
