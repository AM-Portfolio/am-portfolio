# Redis Service Module

This module provides Redis caching functionality for the Portfolio Management System. It handles caching of portfolio data, market indices, stock prices, and other related information.

## Features

- Caching for Portfolio Analysis and Summary
- Stock Price Caching (Real-time and Historical)
- Market Index Data Caching
- Portfolio Holdings Cache Management
- Redis Health Monitoring
- Redis Metrics Collection

## Configuration

The module uses Spring Boot's configuration properties. Key configurations can be set in `application.yml`:

```yaml
spring:
  data:
    redis:
      redisendpoint: localhost:6379
      portfolio-mover:
        ttl: 300  # 5 minutes
        key-prefix: "portfolio:mover:"
      portfolio-summary:
        ttl: 300  # 5 minutes
        key-prefix: "portfolio:summary:"
      # ... other configurations
```

## Services

- `StockPriceRedisService`: Manages stock price caching
- `MarketIndexIndicesRedisService`: Handles market index data caching
- `PortfolioAnalysisRedisService`: Caches portfolio analysis data
- `PortfolioHoldingsRedisService`: Manages portfolio holdings cache
- `PortfolioSummaryRedisService`: Handles portfolio summary caching

## Health Monitoring

The module includes a Redis health indicator that monitors:
- Redis connection status
- Redis version
- Redis mode
- Operation metrics

## Metrics

The following metrics are collected:
- Cache hits/misses
- Operation timing
- Redis connection status

## Usage

To use this module in your Spring Boot application:

1. Add the module dependency to your `pom.xml`:
```xml
<dependency>
    <groupId>com.portfolio</groupId>
    <artifactId>redis-service</artifactId>
    <version>${project.version}</version>
</dependency>
```

2. The module will auto-configure itself through Spring Boot's auto-configuration mechanism.

3. Inject and use the required services:
```java
@Autowired
private StockPriceRedisService stockPriceRedisService;

@Autowired
private PortfolioSummaryRedisService portfolioSummaryRedisService;
```

## Error Handling

The module includes comprehensive error handling and logging:
- Redis operation exceptions are wrapped in `RedisOperationException`
- All operations are logged using SLF4J
- Failed operations are tracked in metrics

## Dependencies

- Spring Boot
- Spring Data Redis
- Lettuce Redis Client
- Lombok
- Spring Boot Actuator (for metrics and health monitoring)
