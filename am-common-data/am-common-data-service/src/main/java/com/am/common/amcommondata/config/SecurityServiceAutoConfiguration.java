package com.am.common.amcommondata.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.am.common.amcommondata.mapper.SecurityModelMapper;
import com.am.common.amcommondata.repository.security.SecurityRepository;
import com.am.common.amcommondata.service.AuditService;
import com.am.common.amcommondata.service.SecurityService;

/**
 * Auto-configuration for SecurityService to ensure it works properly when included as a dependency
 * in other services.
 */
@AutoConfiguration
@Configuration
@EnableConfigurationProperties(SecurityServiceProperties.class)
@ConditionalOnProperty(prefix = "am.common.security", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = {
    "com.am.common.amcommondata.service",
    "com.am.common.amcommondata.repository.security",
    "com.am.common.amcommondata.mapper"
})
public class SecurityServiceAutoConfiguration {
    
    private static final Logger log = LoggerFactory.getLogger(SecurityServiceAutoConfiguration.class);
    
    @Bean
    @ConditionalOnMissingBean
    public SecurityService securityService(
            SecurityRepository securityRepository,
            SecurityModelMapper securityMapper,
            AuditService auditService,
            SecurityServiceProperties properties) {
        
        log.info("Initializing SecurityService with properties: {}", properties);
        return new SecurityService(securityRepository, securityMapper, auditService);
    }
}
