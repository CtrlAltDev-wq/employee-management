package com.wangqi.onboarding.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Validates tokens issued by the employee API.
 *
 * <p>Only the signature, expiry and two claims matter here: the subject and the
 * {@code role} claim. There is no user table in this service, and deliberately
 * no database round trip — authorities come from the signed token, which is
 * what makes the check stateless and horizontally scalable.
 *
 * @author wangqi
 */
@Service
public class JwtService {

    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @return the authenticated principal, or empty for a malformed, tampered,
     *         expired or incomplete token. Callers treat empty as "not authenticated".
     */
    public Optional<JwtPrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String subject = claims.getSubject();
            String role = claims.get("role", String.class);
            if (subject == null || role == null) {
                return Optional.empty();
            }
            return Optional.of(new JwtPrincipal(subject, role));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public record JwtPrincipal(String subject, String role) {
    }
}
