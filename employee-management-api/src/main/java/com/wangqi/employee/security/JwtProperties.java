package com.wangqi.employee.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT settings bound from the {@code app.jwt} prefix.
 *
 * @param secret     HS256 signing key, at least 32 bytes long
 * @param expiration how long an issued token stays valid
 * @author wangqi
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration expiration) {
}
