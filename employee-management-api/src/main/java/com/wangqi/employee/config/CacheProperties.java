package com.wangqi.employee.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cache tuning knobs. TTLs are deliberately short: cached reads are an
 * optimisation, not a source of truth, so a stale entry should disappear on its
 * own even if an eviction is missed.
 *
 * @param provider      {@code redis} (default) or {@code simple} for tests
 * @param employeeTtl   time-to-live for cached single-employee reads
 * @param departmentTtl time-to-live for the cached department listing
 * @author wangqi
 */
@ConfigurationProperties(prefix = "app.cache")
public record CacheProperties(String provider, Duration employeeTtl, Duration departmentTtl) {
}
