package com.wangqi.onboarding.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the onboarding service accepts tokens signed by the employee
 * API and rejects anything else, without touching a database.
 *
 * @author wangqi
 */
class JwtServiceTest {

    private static final String SECRET =
            "wangqi-employee-api-dev-secret-key-change-me-in-production-0123456789";

    private final JwtService jwtService = new JwtService(
            new JwtProperties(SECRET, Duration.ofHours(1)));

    @Test
    @DisplayName("a token signed with the shared secret yields subject and role")
    void shouldParseValidToken() {
        String token = token("viewer", "ROLE_USER", Instant.now().plusSeconds(600));

        assertThat(jwtService.parse(token))
                .contains(new JwtService.JwtPrincipal("viewer", "ROLE_USER"));
    }

    @Test
    @DisplayName("a tampered token is rejected")
    void shouldRejectTamperedToken() {
        String token = token("admin", "ROLE_ADMIN", Instant.now().plusSeconds(600));

        assertThat(jwtService.parse(token + "x")).isEmpty();
    }

    @Test
    @DisplayName("an expired token is rejected")
    void shouldRejectExpiredToken() {
        String token = token("admin", "ROLE_ADMIN", Instant.now().minusSeconds(60));

        assertThat(jwtService.parse(token)).isEmpty();
    }

    @Test
    @DisplayName("a token without the role claim is rejected")
    void shouldRejectTokenWithoutRole() {
        String token = Jwts.builder()
                .subject("admin")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(jwtService.parse(token)).isEmpty();
    }

    private String token(String subject, String role, Instant expiresAt) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
