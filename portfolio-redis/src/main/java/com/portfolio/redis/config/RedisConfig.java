package com.portfolio.redis.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.portfolio.model.market.IndexIndices;
import com.portfolio.model.cache.StockIndicesEventDataCache;
import com.portfolio.model.cache.StockPriceCache;
import com.portfolio.model.portfolio.PortfolioAnalysis;
import com.portfolio.model.portfolio.PortfolioHoldings;
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;

@Configuration
@EnableCaching
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.password}")
    private String redisPassword;

    @Value("${market.data.cache.ttl.seconds:300}")
    private long cacheTimeToLiveSeconds;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        
        log.info("Redis connection details - Host: {}, Port: {}", redisHost, 6379);
        log.info("Redis password is {}", redisPassword != null && !redisPassword.isEmpty() ? "provided" : "not provided");
        
        redisConfig.setHostName(redisHost);
        //redisConfig.setPort(6379);
        
        if (redisPassword != null && !redisPassword.isEmpty()) {
            log.info("Setting Redis password for authentication");
            redisConfig.setPassword(redisPassword);
        } else {
            log.warn("No Redis password provided, connecting without authentication");
        }
        
        return new LettuceConnectionFactory(redisConfig);
    }

    @Bean
    @Primary
    @Qualifier("redisObjectMapper")
    public ObjectMapper redisObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }
    
    /**
     * Creates a RedisTemplate for the given connection factory and target class.
     *
     * @param <T> the type parameter
     * @param connectionFactory the Redis connection factory
     * @param targetClass the target class for serialization
     * @return a configured RedisTemplate instance
     */
    private <T> RedisTemplate<String, T> createRedisTemplate(RedisConnectionFactory connectionFactory, Class<T> targetClass) {
        RedisTemplate<String, T> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        
        // Create Jackson serializer with JSR310 support using the non-deprecated approach
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Use the newer constructor that takes the ObjectMapper directly
        Jackson2JsonRedisSerializer<T> serializer = new Jackson2JsonRedisSerializer<>(objectMapper, targetClass);
        
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
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
    public RedisTemplate<String, StockIndicesEventDataCache> stockIndicesRedisTemplate(RedisConnectionFactory connectionFactory) {
        return createRedisTemplate(connectionFactory, StockIndicesEventDataCache.class);
    }

    @Bean
    public RedisTemplate<String, IndexIndices> marketIndexIndicesRedisTemplate(RedisConnectionFactory connectionFactory) {
        return createRedisTemplate(connectionFactory, IndexIndices.class);
    }
    
    /**
     * Creates a generic RedisTemplate bean that can be used for general Redis operations.
     * This bean is marked as @Primary so it will be the default when autowiring RedisTemplate.
     *
     * @param connectionFactory the Redis connection factory
     * @return a configured RedisTemplate instance for generic Object values
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        
        // Use the newer constructor that takes the ObjectMapper directly
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(mapper, Object.class);
        
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        
        return template;
    }
}
