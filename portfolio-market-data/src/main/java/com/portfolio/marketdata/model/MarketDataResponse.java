package com.portfolio.marketdata.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the market data response for a single financial instrument.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketDataResponse {
    private long instrumentToken;
    private double lastPrice;
    private OhlcData ohlc;
}
