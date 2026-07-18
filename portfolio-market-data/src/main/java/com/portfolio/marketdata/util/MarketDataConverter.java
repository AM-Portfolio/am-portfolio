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

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for converting between different market data model formats.
 * Provides conversion methods between the unified MarketData model and other models.
 */
@Slf4j
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
        
        Double effectivePreviousClose = response.getPreviousClose();

        // Avoid emitting previousClose without a usable lastPrice (prevents NPEs and -100% drops)
        if (response.getLastPrice() <= 0) {
            effectivePreviousClose = null;
        }

        MarketDataBuilder builder = MarketData.builder()
            .instrumentToken(response.getInstrumentToken())
            .lastPrice(response.getLastPrice())
            .previousClose(effectivePreviousClose)
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
                .timestamp(point.getTime() != null ? point.getTime().atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant() : Instant.now())
                .ohlcData(OhlcData.builder()
                    .open(point.getOpen())
                    .high(point.getHigh())
                    .low(point.getLow())
                    .close(point.getClose())
                    .build())
                .volume(point.getVolume())
                .build());
        }
        
        MarketDataBuilder builder = MarketData.builder()
            .symbol(response.getSymbol())
            .fromDate(response.getFromDate())
            .toDate(response.getToDate())
            .timeFrame(timeFrame)
            .historical(true)
            .dataPoints(dataPoints);

        if (!dataPoints.isEmpty()) {
            MarketData.MarketDataPoint latestPoint = dataPoints.get(dataPoints.size() - 1);
            MarketData.MarketDataPoint firstPoint = dataPoints.get(0);
            
            builder.ohlc(latestPoint.getOhlcData());
            if (latestPoint.getOhlcData() != null) {
                builder.lastPrice(latestPoint.getOhlcData().getClose());
            }
            if (dataPoints.size() >= 2) {
                MarketData.MarketDataPoint prevPoint = dataPoints.get(dataPoints.size() - 2);
                if (prevPoint.getOhlcData() != null && latestPoint.getOhlcData() != null && latestPoint.getOhlcData().getClose() > 0) {
                    builder.previousClose(prevPoint.getOhlcData().getClose());
                }
            }
        }
        
        return builder.build();
    }
}
