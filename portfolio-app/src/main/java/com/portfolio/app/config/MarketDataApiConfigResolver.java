package com.portfolio.app.config;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class that resolves the MarketDataApiConfig bean ambiguity
 */
@Configuration
public class MarketDataApiConfigResolver {
    
    /**
     * Creates a bean definition registry post processor that removes the duplicate MarketDataApiConfig bean
     * 
     * @return BeanDefinitionRegistryPostProcessor
     */
    @Bean
    public BeanDefinitionRegistryPostProcessor marketDataApiConfigProcessor() {
        return new BeanDefinitionRegistryPostProcessor() {
            @Override
            public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
                // Remove the bean with the ConfigurationProperties generated name
                if (registry.containsBeanDefinition("market-data.api-com.portfolio.marketdata.config.MarketDataApiConfig")) {
                    registry.removeBeanDefinition("market-data.api-com.portfolio.marketdata.config.MarketDataApiConfig");
                }
            }
            
            @Override
            public void postProcessBeanFactory(org.springframework.beans.factory.config.ConfigurableListableBeanFactory beanFactory) {
                // No additional processing needed here
            }
        };
    }
}
