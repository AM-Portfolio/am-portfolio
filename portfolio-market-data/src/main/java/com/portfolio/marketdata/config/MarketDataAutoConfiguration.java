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

import org.springframework.web.reactive.function.client.WebClient;

/**
 * Auto-configuration for the Market Data API module.
 */
@Configuration
@EnableConfigurationProperties(value = {MarketDataApiConfig.class})
@ComponentScan(basePackages = "com.portfolio.marketdata")
public class MarketDataAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MarketDataApiClient marketDataApiClient(WebClient.Builder webClientBuilder, MarketDataApiConfig config) {
        return new MarketDataApiClient(webClientBuilder, config);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public MarketDataService marketDataService(
            MarketDataApiClient marketDataApiClient,
            org.springframework.beans.factory.ObjectProvider<com.portfolio.redis.service.PortfolioMarketDataRedisService> marketDataRedisServiceProvider,
            org.springframework.beans.factory.ObjectProvider<com.am.common.amcommondata.service.price.StockPriceMongoService> stockPriceMongoServiceProvider,
            org.springframework.beans.factory.ObjectProvider<com.am.common.amcommondata.service.price.StockPriceHistoryMongoService> stockPriceHistoryMongoServiceProvider,
            @org.springframework.beans.factory.annotation.Qualifier("taskExecutor") java.util.concurrent.Executor taskExecutor,
            @org.springframework.beans.factory.annotation.Qualifier("externalApiExecutor") java.util.concurrent.Executor externalApiExecutor) {
        return new MarketDataService(
                marketDataApiClient, 
                marketDataRedisServiceProvider.getIfAvailable(), 
                stockPriceMongoServiceProvider.getIfAvailable(), 
                stockPriceHistoryMongoServiceProvider.getIfAvailable(),
                taskExecutor,
                externalApiExecutor);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public NseIndicesApiClient nseIndicesApiClient(WebClient.Builder webClientBuilder, MarketDataApiConfig config) {
        return new NseIndicesApiClient(webClientBuilder, config);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public NseIndicesService nseIndicesService(NseIndicesApiClient nseIndicesApiClient) {
        return new NseIndicesService(nseIndicesApiClient);
    }
}
