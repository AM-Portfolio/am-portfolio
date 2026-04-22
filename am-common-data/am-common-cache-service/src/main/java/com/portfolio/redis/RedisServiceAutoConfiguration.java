package com.portfolio.redis;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

import com.portfolio.redis.config.RedisProperties;

@AutoConfiguration
@ComponentScan(basePackages = "com.portfolio.redis")
@EnableConfigurationProperties(RedisProperties.class)
public class RedisServiceAutoConfiguration {
}
