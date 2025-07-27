package com.portfolio.analytics.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for the portfolio-analytics module
 * Ensures that Spring can discover and load all components in this module
 */
@Configuration
@ComponentScan(basePackages = {"com.portfolio.analytics"})
public class AnalyticsModuleConfig {
    // Configuration is handled by annotations
}
