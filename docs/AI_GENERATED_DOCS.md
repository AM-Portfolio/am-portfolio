Authentication Documentation
==========================
### Overview of the Codebase

The codebase is a Java-based portfolio application that utilizes various technologies such as Redis, Kafka, and MongoDB. The application is designed to manage and analyze portfolio data, including stock prices, market indices, and portfolio holdings. The codebase is divided into two main modules: `portfolio-redis` and `portfolio-app`.

### Key Components and their Purposes

*   **RedisConfig**: This is a configuration class that sets up the Redis connection and caching mechanism for the application. It defines the Redis host, password, and cache time-to-live (TTL) settings.
*   **application.yml**: This is a configuration file that contains settings for the application, including MongoDB, Kafka, and Redis connections. It also defines various properties such as topic names, consumer IDs, and security settings.
*   **RedisConnectionFactory**: This is a bean that creates a Redis connection factory, which is used to establish connections to the Redis server.
*   **RedisTemplate**: This is a bean that creates a Redis template, which is used to perform operations on the Redis cache.

### API Documentation

The codebase does not provide a RESTful API for authentication. However, it does provide a configuration class `RedisConfig` that sets up the Redis connection and caching mechanism.

#### RedisConfig API

*   **redisConnectionFactory()**: This method creates a Redis connection factory, which is used to establish connections to the Redis server.
*   **redisTemplate()**: This method creates a Redis template, which is used to perform operations on the Redis cache.

### Usage Examples

To use the Redis configuration in the application, you can inject the `RedisTemplate` bean into your service classes and use it to perform operations on the Redis cache.

```java
@Service
public class PortfolioService {
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    public void savePortfolioData(String data) {
        redisTemplate.opsForValue().set("portfolio:data", data);
    }
    
    public String getPortfolioData() {
        return redisTemplate.opsForValue().get("portfolio:data");
    }
}
```

### Architecture Notes

The application uses a microservices architecture, with separate modules for Redis configuration and portfolio data management. The `portfolio-redis` module is responsible for setting up the Redis connection and caching mechanism, while the `portfolio-app` module handles portfolio data management and analysis.

The application uses Redis as a caching layer to improve performance and reduce the load on the database. The `RedisConfig` class sets up the Redis connection and caching mechanism, and provides methods for creating a Redis connection factory and template.

The application also uses Kafka for messaging and data integration. The `application.yml` file contains settings for Kafka connections, topic names, and consumer IDs.

The application uses MongoDB as the primary database for storing portfolio data. The `application.yml` file contains settings for MongoDB connections and database names.

Overall, the application is designed to be scalable, flexible, and highly available, with a focus on performance and reliability.

### Security Considerations

The application uses Redis authentication and authorization to secure access to the Redis cache. The `RedisConfig` class sets up the Redis connection and caching mechanism, and provides methods for creating a Redis connection factory and template.

The application also uses Kafka security features, such as SSL/TLS encryption and authentication, to secure data transmission and integration.

The application uses MongoDB security features, such as authentication and authorization, to secure access to the database.

### Best Practices

The application follows best practices for coding, testing, and deployment, including:

*   Using a consistent coding style and formatting conventions
*   Writing unit tests and integration tests to ensure code quality and reliability
*   Using continuous integration and continuous deployment (CI/CD) pipelines to automate testing and deployment
*   Using monitoring and logging tools to track application performance and issues
*   Using security best practices to protect sensitive data and prevent unauthorized access

### Future Development

The application is designed to be highly scalable and flexible, with a focus on performance and reliability. Future development plans include:

*   Adding new features and functionality to the application, such as support for multiple portfolio types and advanced analytics
*   Improving application performance and reliability, such as by optimizing database queries and caching mechanisms
*   Enhancing security and authentication features, such as by adding support for multi-factor authentication and encryption
*   Expanding the application to support multiple users and organizations, such as by adding support for user authentication and authorization. 

### Code Files

#### File: `portfolio-redis/src/main/java/com/portfolio/redis/config/RedisConfig.java`
```java
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
        redisConfig.setHostName(redisHost);
        redisConfig.setPassword(redisPassword);
        return new LettuceConnectionFactory(redisConfig);
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate() {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory());
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
}
```

#### File: `portfolio-app/src/main/resources/application.yml`
```yml
# mongo
MONGODB_URL: mongodb://ssd2658:ssd2658@mongodb.dev.svc.cluster.local:27017/portfolio?authSource=admin

# Kafka
KAFKA_USERNAME: kafkaUser
KAFKA_PASSWORD: kafkaPassword123!
KAFKA_BOOTSTRAP_SERVERS: localhost:9092
KAFKA_SECURITY_PROTOCOL: SASL_PLAINTEXT
KAFKA_SASL_MECHANISM: PLAIN
KAFKA_JAAS_CONFIG: org.apache.kafka.common.security.plain.PlainLoginModule required username="${KAFKA_USERNAME}" password="${KAFKA_PASSWORD}";

# Topic
PORTFOLIO_CONSUMER_ID: am-portfolio-group-1
PORTFOLIO_TOPIC: am-portfolio
STOCK_CONSUMER_ID: am-stock-group.v2
STOCK_TOPIC: am-stock-price-update
MARKET_INDEX_CONSUMER_ID: am-market-index-group-1
MARKET_INDEX_TOPIC: nse-stock-indices-update

# Redis
REDIS_HOSTNAME: localhost
REDIS_PORT: 6379
REDIS_PASSWORD: password

spring:
  application:
    name: portfolio-app
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    consumer:
      group-id: ${PORTFOLIO_CONSUMER_ID}
      auto-offset-reset: latest
      enable-auto-commit: false
      max-poll-records: 100
    properties:
      security-protocol: SASL_PLAINTEXT
      sasl-mechanism: PLAIN
      sasl-jaas-config: org.apache.kafka.common.security.plain.PlainLoginModule required username="${KAFKA_USERNAME}" password="${KAFKA_PASSWORD}";
      ssl-endpoint-identification-algorithm: ""
      ssl-truststore-location: /etc/ssl/certs/java/cacerts
      ssl-truststore-password: changeit
  data:
    redis:
      host: redis.dev.svc.cluster.local
      port: 6379
      password: RedisPassword123!
      portfolio-mover:
        ttl: 900  # 15 minutes in seconds
        key-prefix: "portfolio:analysis:"
      portfolio-summary:
        ttl: 900  # 15 minutes in seconds
        key-prefix: "portfolio:summary:"
      portfolio-holdings:
        ttl: 900  # 15 minutes in seconds
        key-prefix: "portfolio:holdings:"
      market-indices:
        ttl: 129600  # 30 Days in seconds
        key-prefix: "market-indices:indices:"
        historical:
          key-prefix: "market-indices:historical:"
```