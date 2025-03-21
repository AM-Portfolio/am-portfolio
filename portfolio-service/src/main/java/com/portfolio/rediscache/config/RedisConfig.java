package com.portfolio.rediscache.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.model.MarketIndexIndicesCache;
import com.portfolio.model.StockPriceCache;
import com.portfolio.model.portfolio.PortfolioAnalysis;
import com.portfolio.model.portfolio.PortfolioHoldings;
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;

@Configuration
@EnableCaching
public class RedisConfig {

    private <T> RedisTemplate<String, T> createRedisTemplate(RedisConnectionFactory connectionFactory, Class<T> clazz) {
        RedisTemplate<String, T> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        
        Jackson2JsonRedisSerializer<T> serializer = new Jackson2JsonRedisSerializer<>(mapper, clazz);
        
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        
        return template;
    }

    @Bean
    public RedisTemplate<String, StockPriceCache> stockPriceRedisTemplate(RedisConnectionFactory connectionFactory) {
        return createRedisTemplate(connectionFactory, StockPriceCache.class);
    }

    @Bean
    public RedisTemplate<String, PortfolioAnalysis> portfolioAnalysisRedisTemplate(RedisConnectionFactory connectionFactory) {
        return createRedisTemplate(connectionFactory, PortfolioAnalysis.class);
    }

    @Bean
    public RedisTemplate<String, PortfolioHoldings> portfolioHoldingsRedisTemplate(RedisConnectionFactory connectionFactory) {
        return createRedisTemplate(connectionFactory, PortfolioHoldings.class);
    }

    @Bean
    public RedisTemplate<String, PortfolioSummaryV1> portfolioSummaryRedisTemplate(RedisConnectionFactory connectionFactory) {
        return createRedisTemplate(connectionFactory, PortfolioSummaryV1.class);
    }

    @Bean
    public RedisTemplate<String, MarketIndexIndicesCache> marketIndexIndicesRedisTemplate(RedisConnectionFactory connectionFactory) {
        return createRedisTemplate(connectionFactory, MarketIndexIndicesCache.class);
    }
}
