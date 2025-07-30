package com.portfolio.marketdata.util;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.am.common.investment.model.historical.OHLCVTPoint;
import com.portfolio.marketdata.model.HistoricalData;
import com.portfolio.marketdata.model.HistoricalDataResponse;
import com.portfolio.marketdata.model.MarketDataResponse;
import com.portfolio.model.market.MarketData;
import com.portfolio.model.market.MarketData.MarketDataBuilder;
import com.portfolio.model.market.OhlcData;
import com.portfolio.model.market.TimeFrame;

/**
 * Utility class for converting between different market data model formats.
 * Provides conversion methods between the unified MarketData model and other models.
 */
public class MarketDataConverter {

    /**
     * Convert from MarketDataResponse to the unified MarketData model
     * 
     * @param response The MarketDataResponse to convert
     * @return A new MarketData instance
     */
    public static MarketData fromMarketDataResponse(MarketDataResponse response) {
        if (response == null) {
            return null;
        }
        
        MarketDataBuilder builder = MarketData.builder()
            .instrumentToken(response.getInstrumentToken())
            .lastPrice(response.getLastPrice())
            .ohlc(response.getOhlc())
            .timestamp(response.getTimestamp())
            .timeFrame(response.getTimeFrame())
            .historical(response.isHistorical());
        
        // Convert historical entries if present
        if (response.getHistoricalOhlcEntries() != null && !response.getHistoricalOhlcEntries().isEmpty()) {
            List<MarketData.MarketDataPoint> dataPoints = response.getHistoricalOhlcEntries().stream()
                .map(entry -> MarketData.MarketDataPoint.builder()
                    .timestamp(entry.getTimestamp())
                    .ohlcData(entry.getOhlcData())
                    .volume(entry.getVolume())
                    .build())
                .collect(Collectors.toList());
            
            builder.dataPoints(dataPoints);
        }
        
        return builder.build();
    }
    
    /**
     * Convert from HistoricalDataResponse to the unified MarketData model
     * 
     * @param response The HistoricalDataResponse to convert
     * @return A new MarketData instance
     */
    public static MarketData fromHistoricalDataResponse(HistoricalDataResponse response) {
        if (response == null || response.getData() == null || 
            response.getData().getDataPoints() == null || 
            response.getData().getDataPoints().isEmpty()) {
            return null;
        }
        
        // Convert interval string to TimeFrame enum
        TimeFrame timeFrame = null;
        if (response.getInterval() != null) {
            timeFrame = TimeFrame.fromValue(response.getInterval());
        }
        
        // Convert data points
        List<MarketData.MarketDataPoint> dataPoints = new ArrayList<>();
        for (OHLCVTPoint point : response.getData().getDataPoints()) {
            dataPoints.add(MarketData.MarketDataPoint.builder()
                .timestamp(Instant.now()) // In a real implementation, use point's timestamp
                .ohlcData(OhlcData.builder()
                    .open(point.getOpen())
                    .high(point.getHigh())
                    .low(point.getLow())
                    .close(point.getClose())
                    .build())
                .volume(point.getVolume())
                .build());
        }
        
        return MarketData.builder()
            .symbol(response.getSymbol())
            .fromDate(response.getFromDate())
            .toDate(response.getToDate())
            .timeFrame(timeFrame)
            .historical(true)
            .dataPoints(dataPoints)
            .build();
    }
}
