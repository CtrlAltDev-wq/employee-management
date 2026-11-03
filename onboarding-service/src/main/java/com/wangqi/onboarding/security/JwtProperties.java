package com.wangqi.onboarding.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT settings bound from the {@code app.jwt} prefix.
 *
 * <p>The secret is the one the employee API signs with. A shared HS256 key is a
 * demo simplification: production would publish a JWKS and validate an
 * asymmetric signature so only the issuer holds the private key.
 *
 * @param secret     HS256 key, at least 32 bytes long
 * @param expiration documented token lifetime (validation relies on the exp claim)
 * @author wangqi
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration expiration) {
}
