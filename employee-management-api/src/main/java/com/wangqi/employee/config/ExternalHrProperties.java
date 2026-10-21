package com.wangqi.employee.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings for the outbound HR integration.
 *
 * @param baseUrl        root URL of the third-party service
 * @param maxAttempts    total attempts, including the first one
 * @param initialBackoff delay before the second attempt; doubles on every retry
 * @param timeout        connect and read timeout for a single attempt
 * @author wangqi
 */
@ConfigurationProperties(prefix = "app.external-hr")
public record ExternalHrProperties(
        String baseUrl,
        int maxAttempts,
        Duration initialBackoff,
        Duration timeout
) {
}
