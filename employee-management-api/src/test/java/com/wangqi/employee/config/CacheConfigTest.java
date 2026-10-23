package com.wangqi.employee.config;

import com.wangqi.employee.dto.DepartmentResponse;
import com.wangqi.employee.dto.EmployeeResponse;
import com.wangqi.employee.dto.EmployeeSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for the Redis cache wiring that do not need a Redis server: the
 * serializer is exercised directly and the cache manager is built on a mocked
 * connection factory (caches are created lazily, so no connection is opened).
 *
 * <p>The round-trip tests are the important ones: a cache that cannot
 * deserialize what it wrote fails only on the <em>second</em> request, which is
 * exactly the kind of bug that escapes a happy-path smoke test.
 *
 * @author wangqi
 */
class CacheConfigTest {

    private final CacheConfig cacheConfig = new CacheConfig();
    private final GenericJackson2JsonRedisSerializer serializer = cacheConfig.jsonSerializer();

    @Test
    @DisplayName("EmployeeResponse (record + Instant + BigDecimal) survives a JSON round trip")
    void shouldRoundTripEmployeeResponse() {
        EmployeeResponse original = new EmployeeResponse(
                7L, "Alice", "Chen", "alice@example.com", "Senior Backend Engineer",
                new BigDecimal("9000.00"), Instant.parse("2024-01-01T10:15:30Z"));

        Object restored = serializer.deserialize(serializer.serialize(original));

        assertThat(restored)
                .isInstanceOf(EmployeeResponse.class)
                .isEqualTo(original);
    }

    @Test
    @DisplayName("Cached department list keeps its concrete list type and nested records")
    void shouldRoundTripDepartmentList() {
        List<DepartmentResponse> original = new ArrayList<>();
        original.add(new DepartmentResponse(1L, "Engineering", new ArrayList<>(
                List.of(new EmployeeSummary(1L, "Alice", "Chen", "alice@example.com", "Backend Engineer")))));
        original.add(new DepartmentResponse(2L, "Sales", new ArrayList<>()));

        Object restored = serializer.deserialize(serializer.serialize(original));

        assertThat(restored).isEqualTo(original);
        assertThat((List<?>) restored).isInstanceOf(ArrayList.class);
        assertThat(((List<?>) restored).get(0)).isInstanceOf(DepartmentResponse.class);
    }

    @Test
    @DisplayName("Each cache gets its own configured TTL")
    void shouldApplyPerCacheTtl() {
        CacheProperties properties = new CacheProperties("redis", Duration.ofMinutes(10), Duration.ofMinutes(5));

        RedisCacheManager cacheManager =
                cacheConfig.cacheManager(mock(RedisConnectionFactory.class), properties);
        // Mirrors the container lifecycle: initialises the caches declared with
        // their own TTL instead of creating them lazily with the defaults.
        cacheManager.afterPropertiesSet();

        Map<String, RedisCacheConfiguration> configurations = cacheManager.getCacheConfigurations();

        assertThat(configurations.get(CacheConfig.EMPLOYEES).getTtl()).isEqualTo(Duration.ofMinutes(10));
        assertThat(configurations.get(CacheConfig.DEPARTMENTS).getTtl()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("Null values are never cached, so an unknown id keeps returning 404")
    void shouldDisableCachingNullValues() {
        assertThat(cacheConfig.defaultConfiguration().getAllowCacheNullValues()).isFalse();
    }
}
