package com.portfolio.marketdata.model;

import com.portfolio.model.market.OhlcData;
import com.portfolio.model.market.TimeFrame;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the market data response for a single financial instrument.
 * Can represent either current market data or a snapshot from historical data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketDataResponse {
    private long instrumentToken;
    private double lastPrice;
    private Double previousClose;
    private OhlcData ohlc;
    
    // Fields for historical data context
    @Builder.Default
    private boolean historical = false;
    private Instant timestamp;
    private TimeFrame timeFrame;
    
    // For historical data with multiple OHLC entries
    @Builder.Default
    private List<HistoricalOhlcEntry> historicalOhlcEntries = new ArrayList<>();
    
    /**
     * Represents a single historical OHLC entry with timestamp
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoricalOhlcEntry {
        private Instant timestamp;
        private OhlcData ohlcData;
        private double volume;
    }
    
    /**
     * Factory method to create a MarketDataResponse from historical data
     * 
     * @param historicalResponse The historical data response
     * @param latestPointIndex Index of the data point to use for current values (typically the latest)
     * @return A new MarketDataResponse with historical context
     */
    public static MarketDataResponse fromHistoricalData(HistoricalDataResponse historicalResponse, int latestPointIndex) {
        if (historicalResponse == null) {
            return null;
        }
        var dataPoints = historicalResponse.effectiveDataPoints();
        if (dataPoints.isEmpty() || latestPointIndex >= dataPoints.size()) {
            return null;
        }

        var latestPoint = dataPoints.get(latestPointIndex);
        String interval = historicalResponse.getInterval();
        if (interval == null && historicalResponse.getData() != null) {
            interval = historicalResponse.getData().getInterval();
        }

        // Create the main response with the latest point as current data
        MarketDataResponse response = MarketDataResponse.builder()
            .instrumentToken(0) // Default value
            .lastPrice(latestPoint.getClose())
            .ohlc(OhlcData.builder()
                .open(latestPoint.getOpen())
                .high(latestPoint.getHigh())
                .low(latestPoint.getLow())
                .close(latestPoint.getClose())
                .build())
            .historical(true)
            .timestamp(Instant.now())
            .timeFrame(interval != null ? TimeFrame.fromValue(interval) : null)
            .build();
        
        // Add all historical data points
        for (var point : dataPoints) {
            response.getHistoricalOhlcEntries().add(
                HistoricalOhlcEntry.builder()
                    .timestamp(Instant.now()) // In a real implementation, use point's timestamp
                    .ohlcData(OhlcData.builder()
                        .open(point.getOpen())
                        .high(point.getHigh())
                        .low(point.getLow())
                        .close(point.getClose())
                        .build())
                    .volume(point.getVolume())
                    .build()
            );
        }
        
        return response;
    }
    
    /**
     * Factory method to create a MarketDataResponse from the latest historical data point
     * 
     * @param historicalResponse The historical data response
     * @return A new MarketDataResponse with historical context
     */
    public static MarketDataResponse fromHistoricalData(HistoricalDataResponse historicalResponse) {
        if (historicalResponse == null) {
            return null;
        }
        var dataPoints = historicalResponse.effectiveDataPoints();
        if (dataPoints.isEmpty()) {
            return null;
        }
        return fromHistoricalData(historicalResponse, dataPoints.size() - 1);
    }
}
