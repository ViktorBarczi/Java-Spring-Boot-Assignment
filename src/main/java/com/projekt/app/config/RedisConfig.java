package com.projekt.app.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis cache configuration.
 *
 * Configures the CacheManager to:
 *  - Use String keys  (human-readable in Redis, e.g. "books::1")
 *  - Use JSON values  (not Java binary serialization — survives app restarts and
 *                      is readable with any Redis client)
 *  - Expire entries after 10 minutes (TTL)
 */
@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
public class RedisConfig {

    /**
     * Builds the Spring CacheManager backed by Redis.
     *
     * GenericJacksonJsonRedisSerializer (Spring Data Redis 4 / Jackson 3):
     *   - Embeds a "@class" field in every JSON value so the correct Java type
     *     is known when deserializing (e.g. BookResponse vs List<BookResponse>).
     *   - enableUnsafeDefaultTyping() is the equivalent of the old
     *     ObjectMapper.activateDefaultTyping() — required so polymorphic
     *     collections like List<BookResponse> round-trip correctly.
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        GenericJacksonJsonRedisSerializer jsonSerializer =
                GenericJacksonJsonRedisSerializer.builder()
                        .enableUnsafeDefaultTyping()
                        .build();

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
