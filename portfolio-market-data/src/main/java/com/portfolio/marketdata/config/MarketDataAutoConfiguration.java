package com.portfolio.marketdata.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.portfolio.marketdata.client.MarketDataApiClient;
import com.portfolio.marketdata.client.NseIndicesApiClient;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.marketdata.service.NseIndicesService;

/**
 * Auto-configuration for the Market Data API module.
 */
@Configuration
@EnableConfigurationProperties(value = {MarketDataApiConfig.class})
@ComponentScan(basePackages = "com.portfolio.marketdata")
public class MarketDataAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MarketDataApiClient marketDataApiClient(MarketDataApiConfig config) {
        return new MarketDataApiClient(config);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public MarketDataService marketDataService(MarketDataApiClient marketDataApiClient) {
        return new MarketDataService(marketDataApiClient);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public NseIndicesApiClient nseIndicesApiClient(MarketDataApiConfig config) {
        return new NseIndicesApiClient(config);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public NseIndicesService nseIndicesService(NseIndicesApiClient nseIndicesApiClient) {
        return new NseIndicesService(nseIndicesApiClient);
    }
}
