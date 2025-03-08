package com.portfolio.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableCaching
@EnableAspectJAutoProxy
public class CacheConfig {
    // Spring Boot will auto-configure Caffeine cache based on application.yml
}
