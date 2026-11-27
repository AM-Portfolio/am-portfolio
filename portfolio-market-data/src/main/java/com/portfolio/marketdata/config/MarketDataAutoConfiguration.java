package com.portfolio.marketdata.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.portfolio.marketdata.client.MarketCalendarClient;
import com.portfolio.marketdata.client.MarketDataApiClient;
import com.portfolio.marketdata.client.NseIndicesApiClient;
import com.portfolio.marketdata.service.DefaultCashSessionClock;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.marketdata.service.NseIndicesService;
import com.portfolio.redis.session.CashSessionClock;

import org.springframework.web.reactive.function.client.WebClient;

/**
 * Auto-configuration for the Market Data API module.
 */
@Configuration
@EnableConfigurationProperties(value = {MarketDataApiConfig.class})
@ComponentScan(basePackages = "com.portfolio.marketdata")
public class MarketDataAutoConfiguration {

    /**
     * AMFI is an external fallback and must never run on the portfolio request
     * thread. Keep one bounded worker so a slow portal cannot grow an unbounded
     * queue of duplicate refreshes.
     */
    @Bean(name = "mfNavFallbackExecutor")
    public java.util.concurrent.Executor mfNavFallbackExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("MfNavFallback-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(2);
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean
    public MarketDataApiClient marketDataApiClient(WebClient.Builder webClientBuilder, MarketDataApiConfig config) {
        return new MarketDataApiClient(webClientBuilder, config);
    }

    @Bean
    @ConditionalOnMissingBean(CashSessionClock.class)
    public CashSessionClock cashSessionClock(MarketCalendarClient marketCalendarClient) {
        return new DefaultCashSessionClock(marketCalendarClient);
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
