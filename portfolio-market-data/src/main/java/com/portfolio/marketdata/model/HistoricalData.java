package com.portfolio.marketdata.model;

import java.util.List;

import com.am.common.investment.model.historical.OHLCVTPoint;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model representing historical data with a list of data points.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HistoricalData {
    
    /**
     * The trading symbol for this historical data
     */
    private String tradingSymbol;
    
    /**
     * The time interval for the data points (e.g., "day", "15min")
     */
    private String interval;
    
    /**
     * List of data points containing OHLCV data
     */
    private List<OHLCVTPoint> dataPoints;
    
    /**
     * Count of data points
     */
    private int dataPointCount;
}
