package com.wangqi.employee.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JwtService}: signing, parsing, expiry and tampering.
 *
 * @author wangqi
 */
class JwtServiceTest {

    private static final String SECRET =
            "wangqi-test-secret-key-that-is-long-enough-for-hs256-signing-0123456789";
    private static final String OTHER_SECRET =
            "another-test-secret-key-that-is-long-enough-for-hs256-signing-9876543210";

    private final UserDetails user = User.withUsername("admin")
            .password("irrelevant")
            .roles("ADMIN")
            .build();

    @Test
    @DisplayName("a generated token can be parsed back to the same username")
    void shouldGenerateAndParseToken() {
        JwtService jwtService = new JwtService(new JwtProperties(SECRET, Duration.ofHours(1)));

        String token = jwtService.generateToken(user);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("admin");
        assertThat(jwtService.isTokenValid(token, user)).isTrue();
        assertThat(jwtService.expirationSeconds()).isEqualTo(3600);
    }

    @Test
    @DisplayName("a token is rejected when the subject does not match the user")
    void shouldRejectTokenForDifferentUser() {
        JwtService jwtService = new JwtService(new JwtProperties(SECRET, Duration.ofHours(1)));
        UserDetails someoneElse = User.withUsername("viewer").password("irrelevant").roles("USER").build();

        String token = jwtService.generateToken(user);

        assertThat(jwtService.isTokenValid(token, someoneElse)).isFalse();
    }

    @Test
    @DisplayName("an expired token is rejected instead of throwing")
    void shouldRejectExpiredToken() {
        JwtService jwtService = new JwtService(new JwtProperties(SECRET, Duration.ofSeconds(-60)));

        String token = jwtService.generateToken(user);

        assertThat(jwtService.isTokenValid(token, user)).isFalse();
    }

    @Test
    @DisplayName("a token signed with another key is rejected")
    void shouldRejectTokenSignedWithAnotherKey() {
        JwtService issuer = new JwtService(new JwtProperties(SECRET, Duration.ofHours(1)));
        JwtService verifier = new JwtService(new JwtProperties(OTHER_SECRET, Duration.ofHours(1)));

        String token = issuer.generateToken(user);

        assertThat(verifier.isTokenValid(token, user)).isFalse();
    }

    @Test
    @DisplayName("a tampered token is rejected")
    void shouldRejectTamperedToken() {
        JwtService jwtService = new JwtService(new JwtProperties(SECRET, Duration.ofHours(1)));

        String token = jwtService.generateToken(user);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThat(jwtService.isTokenValid(tampered, user)).isFalse();
    }
}
