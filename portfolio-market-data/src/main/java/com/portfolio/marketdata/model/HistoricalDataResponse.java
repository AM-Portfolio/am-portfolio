package com.portfolio.marketdata.model;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import com.am.common.investment.model.historical.OHLCVTPoint;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-symbol historical payload from am-market {@code /historical-data}.
 * Market returns a flat shape ({@code tradingSymbol}, {@code dataPoints}); older nested
 * {@code data} is still accepted for tests / legacy callers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HistoricalDataResponse {

    private String symbol;

    @JsonProperty("tradingSymbol")
    private String tradingSymbol;

    private LocalDate fromDate;

    private LocalDate toDate;

    private String interval;

    /** Flat candle list as returned by am-market. */
    private List<OHLCVTPoint> dataPoints;

    private int dataPointCount;

    /** Legacy nested envelope (unit tests / older clients). */
    private HistoricalData data;

    private int count;

    private int originalCount;

    private boolean filtered;

    private String filterType;

    private long processingTimeMs;

    @JsonIgnore
    public List<OHLCVTPoint> effectiveDataPoints() {
        if (dataPoints != null && !dataPoints.isEmpty()) {
            return dataPoints;
        }
        if (data != null && data.getDataPoints() != null) {
            return data.getDataPoints();
        }
        return Collections.emptyList();
    }

    @JsonIgnore
    public String effectiveSymbol() {
        if (symbol != null && !symbol.isBlank()) {
            return symbol;
        }
        return tradingSymbol;
    }
}
