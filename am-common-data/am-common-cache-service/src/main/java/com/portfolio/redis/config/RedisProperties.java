package com.portfolio.redis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "spring.data.redis")
public class RedisProperties {
    private String redisendpoint;
    private PortfolioMover portfolioMover = new PortfolioMover();
    private PortfolioSummary portfolioSummary = new PortfolioSummary();
    private PortfolioHoldings portfolioHoldings = new PortfolioHoldings();
    private MarketIndices marketIndices = new MarketIndices();
    private Stock stock = new Stock();

    @Data
    public static class PortfolioMover {
        private Integer ttl;
        private String keyPrefix;
    }

    @Data
    public static class PortfolioSummary {
        private Integer ttl;
        private String keyPrefix;
    }

    @Data
    public static class PortfolioHoldings {
        private Integer ttl;
        private String keyPrefix;
    }

    @Data
    public static class MarketIndices {
        private Integer ttl;
        private String keyPrefix;
        private Historical historical = new Historical();

        @Data
        public static class Historical {
            private String keyPrefix;
            private Integer ttl;
        }
    }

    @Data
    public static class Stock {
        private Integer ttl;
        private String keyPrefix;
        private Historical historical = new Historical();

        @Data
        public static class Historical {
            private String keyPrefix;
            private Integer ttl;
        }
    }
}
