package com.wangqi.employee.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Browser origins that may call the API directly.
 *
 * <p>In development the SPA is same-origin through the Vite proxy and in
 * production Nginx does the same, so this list is only needed when a browser
 * talks to the API without a proxy in front of it.
 *
 * @author wangqi
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins, long maxAgeSeconds) {
}
