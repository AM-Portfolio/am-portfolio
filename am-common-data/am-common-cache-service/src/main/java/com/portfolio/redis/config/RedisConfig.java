package com.portfolio.redis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.redis.model.MarketIndexIndicesCache;
import com.portfolio.redis.model.PortfolioAnalysis;
import com.portfolio.redis.model.PortfolioHoldings;
import com.portfolio.redis.model.PortfolioSummaryV1;
import com.portfolio.redis.model.StockPriceCache;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, PortfolioAnalysis> portfolioAnalysisRedisTemplate(RedisConnectionFactory connectionFactory) {
        return createRedisTemplate(connectionFactory, PortfolioAnalysis.class);
    }

    @Bean
    public RedisTemplate<String, PortfolioSummaryV1> portfolioSummaryRedisTemplate(RedisConnectionFactory connectionFactory) {
        return createRedisTemplate(connectionFactory, PortfolioSummaryV1.class);
    }

    @Bean
    public RedisTemplate<String, PortfolioHoldings> portfolioHoldingsRedisTemplate(RedisConnectionFactory connectionFactory) {
        return createRedisTemplate(connectionFactory, PortfolioHoldings.class);
    }

    @Bean
    public RedisTemplate<String, StockPriceCache> stockPriceRedisTemplate(RedisConnectionFactory connectionFactory) {
        return createRedisTemplate(connectionFactory, StockPriceCache.class);
    }

    @Bean
    public RedisTemplate<String, MarketIndexIndicesCache> marketIndexIndicesRedisTemplate(RedisConnectionFactory connectionFactory) {
        return createRedisTemplate(connectionFactory, MarketIndexIndicesCache.class);
    }

    private <T> RedisTemplate<String, T> createRedisTemplate(RedisConnectionFactory connectionFactory, Class<T> clazz) {
        RedisTemplate<String, T> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Create ObjectMapper with proper configuration
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        
        // Create serializer using the recommended approach (avoiding deprecated setObjectMapper)
        Jackson2JsonRedisSerializer<T> serializer = new Jackson2JsonRedisSerializer<>(mapper, clazz);
        
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        
        return template;
    }
}
