package com.portfolio.model.cache;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis cache model for stock price data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockPriceCache {
    private String symbol;
    private Double closePrice;
    private Instant timestamp;
}
