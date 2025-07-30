package com.portfolio.model.market;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Unified market data model that can represent both current OHLC data and historical data.
 * This model can be used for both getOHLC and historical data operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketData {
    /**
     * Identifier for the instrument
     */
    private String symbol;
    
    /**
     * Alternative identifier (numeric) for the instrument
     */
    private long instrumentToken;
    
    /**
     * Current/latest price of the instrument
     */
    private double lastPrice;
    
    /**
     * Current/latest OHLC data
     */
    private OhlcData ohlc;
    
    /**
     * Timestamp of the data
     */
    private Instant timestamp;
    
    /**
     * Time frame of the data (e.g., DAY, FIFTEEN_MIN)
     */
    private TimeFrame timeFrame;
    
    /**
     * Start date for historical data
     */
    private LocalDate fromDate;
    
    /**
     * End date for historical data
     */
    private LocalDate toDate;
    
    /**
     * Flag indicating whether this is historical data
     */
    @Builder.Default
    private boolean historical = false;
    
    /**
     * List of historical data points
     */
    @Builder.Default
    private List<MarketDataPoint> dataPoints = new ArrayList<>();
    
    /**
     * Represents a single market data point with OHLCV data and timestamp
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarketDataPoint {
        /**
         * Timestamp of this data point
         */
        private Instant timestamp;
        
        /**
         * OHLC data for this point
         */
        private OhlcData ohlcData;
        
        /**
         * Trading volume
         */
        private double volume;
        
        /**
         * Number of trades (if available)
         */
        private Long trades;
    }
    
    /**
     * Factory method to create MarketData from OHLC data
     * 
     * @param symbol The instrument symbol
     * @param instrumentToken The instrument token
     * @param lastPrice The last price
     * @param open The open price
     * @param high The high price
     * @param low The low price
     * @param close The close price
     * @return A new MarketData instance with current OHLC data
     */
    public static MarketData fromOhlc(String symbol, long instrumentToken, double lastPrice, 
                                     double open, double high, double low, double close) {
        return MarketData.builder()
            .symbol(symbol)
            .instrumentToken(instrumentToken)
            .lastPrice(lastPrice)
            .ohlc(OhlcData.builder()
                .open(open)
                .high(high)
                .low(low)
                .close(close)
                .build())
            .timestamp(Instant.now())
            .historical(false)
            .build();
    }
    
    /**
     * Factory method to create MarketData from historical data
     * 
     * @param symbol The instrument symbol
     * @param fromDate Start date
     * @param toDate End date
     * @param timeFrame Time frame
     * @param dataPoints List of historical data points
     * @return A new MarketData instance with historical data
     */
    public static MarketData fromHistorical(String symbol, LocalDate fromDate, LocalDate toDate, 
                                           TimeFrame timeFrame, List<MarketDataPoint> dataPoints) {
        // Use the latest data point for current values
        MarketDataPoint latestPoint = dataPoints.isEmpty() ? null : dataPoints.get(dataPoints.size() - 1);
        
        MarketData marketData = MarketData.builder()
            .symbol(symbol)
            .fromDate(fromDate)
            .toDate(toDate)
            .timeFrame(timeFrame)
            .historical(true)
            .dataPoints(dataPoints)
            .build();
        
        // Set current values from the latest point if available
        if (latestPoint != null) {
            marketData.setLastPrice(latestPoint.getOhlcData().getClose());
            marketData.setOhlc(latestPoint.getOhlcData());
            marketData.setTimestamp(latestPoint.getTimestamp());
        }
        
        return marketData;
    }
    
    /**
     * Convenience method to get the latest data point
     * 
     * @return The latest data point or null if no data points exist
     */
    public MarketDataPoint getLatestDataPoint() {
        if (dataPoints == null || dataPoints.isEmpty()) {
            return null;
        }
        return dataPoints.get(dataPoints.size() - 1);
    }
}
