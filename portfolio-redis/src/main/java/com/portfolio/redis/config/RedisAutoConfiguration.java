package com.portfolio.redis.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for Redis module.
 * This class automatically configures all Redis-related beans and services.
 * Can be enabled/disabled with the 'portfolio.redis.enabled' property.
 */
@Configuration
@ConditionalOnProperty(name = "portfolio.redis.enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = {"com.portfolio.redis.service"})
@Import({RedisConfig.class})
public class RedisAutoConfiguration {
    // Auto-configuration class that imports all necessary Redis configurations
    // and scans for Redis services
}
