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
import com.portfolio.model.PortfolioAnalysis;
import com.portfolio.model.StockPriceCache;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisTemplate<String, StockPriceCache> stockPriceRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, StockPriceCache> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        
        Jackson2JsonRedisSerializer<StockPriceCache> serializer = new Jackson2JsonRedisSerializer<>(StockPriceCache.class);
        serializer.setObjectMapper(mapper);
        
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        
        return template;
    }

    @Bean
    public RedisTemplate<String, PortfolioAnalysis> portfolioAnalysisRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, PortfolioAnalysis> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        
        Jackson2JsonRedisSerializer<PortfolioAnalysis> serializer = new Jackson2JsonRedisSerializer<>(PortfolioAnalysis.class);
        serializer.setObjectMapper(mapper);
        
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        
        return template;
    }

    @Bean
    public RedisTemplate<String, MarketIndexIndicesCache> marketIndexIndicesRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, MarketIndexIndicesCache> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        
        Jackson2JsonRedisSerializer<MarketIndexIndicesCache> serializer = new Jackson2JsonRedisSerializer<>(MarketIndexIndicesCache.class);
        serializer.setObjectMapper(mapper);
        
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        
        return template;
    }
}
