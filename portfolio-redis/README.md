# Portfolio Redis Module

## Overview
This module provides Redis caching functionality for the Portfolio application. It encapsulates all Redis-related configurations and services, improving modularity and separation of concerns.

## Features
- Auto-configurable Redis integration
- Caching for market indices and portfolio data
- Support for both realtime and historical data
- Configurable TTL and key prefixes

## Usage
To use this module, simply add it as a dependency in your project:

```xml
<dependency>
    <groupId>com.portfolio</groupId>
    <artifactId>portfolio-redis</artifactId>
    <version>${project.version}</version>
</dependency>
```

The module will auto-configure itself when included in a Spring Boot application.

## Configuration
The following properties can be configured in your `application.yml`:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      stock-indices:
        ttl: 3600
        key-prefix: stock-indices:
        historical:
          key-prefix: stock-indices:historical:
          ttl: 86400
```

## Services
- `StockIndicesEventRedisService`: Handles caching and retrieval of stock indices event data

## Models
- `IndexIndices`: Market index data model
- `StockPriceCache`: Stock price cache model
- `PortfolioAnalysis`: Portfolio analysis data model
- `PortfolioHoldings`: Portfolio holdings data model
- `PortfolioSummaryV1`: Portfolio summary data model
- `StockIndicesEventDataCache`: Stock indices event data cache model
