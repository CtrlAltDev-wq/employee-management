package com.wangqi.employee.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.Map;

/**
 * Redis-backed cache for read endpoints.
 *
 * <p>Design notes:
 * <ul>
 *   <li>values are stored as JSON (not JDK bytes), so an operator can inspect a
 *       key with {@code redis-cli} and see the actual payload;</li>
 *   <li>keys are namespaced with {@code employee-api:} to avoid collisions when
 *       several services share one Redis instance;</li>
 *   <li>null is never cached, so a lookup for an absent id keeps returning a
 *       real 404 instead of a cached "not found";</li>
 *   <li>each cache has its own TTL, so a stale entry self-heals even if an
 *       eviction is missed.</li>
 * </ul>
 *
 * <p>Tests set {@code app.cache.provider=simple} so they never need a Redis
 * server; this bean is then skipped and Boot provides an in-memory cache.
 *
 * @author wangqi
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties.class)
@ConditionalOnProperty(prefix = "app.cache", name = "provider", havingValue = "redis", matchIfMissing = true)
public class CacheConfig {

    public static final String EMPLOYEES = "employees";
    public static final String DEPARTMENTS = "departments";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                          CacheProperties properties) {
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfiguration())
                // Feeds Micrometer's cache.gets / cache.puts meters, so the hit
                // ratio is visible on /actuator/metrics instead of being guessed.
                .enableStatistics()
                .withInitialCacheConfigurations(Map.of(
                        EMPLOYEES, defaultConfiguration().entryTtl(properties.employeeTtl()),
                        DEPARTMENTS, defaultConfiguration().entryTtl(properties.departmentTtl())))
                .build();
    }

    RedisCacheConfiguration defaultConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> "employee-api:" + cacheName + "::")
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer()));
    }

    /**
     * Type information must be written into the payload: the cache stores
     * {@code Object}, and records are final, so the default
     * {@code NON_FINAL} typing would not record the concrete class and reads
     * would come back as a {@code LinkedHashMap}.
     */
    GenericJackson2JsonRedisSerializer jsonSerializer() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}
