package com.projekt.app.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "none", matchIfMissing = true)
public class CacheConfig {

  @Bean
  public CacheManager cacheManager() {
    // Keep @EnableCaching valid while bypassing all cache reads and writes.
    return new NoOpCacheManager();
  }
}
