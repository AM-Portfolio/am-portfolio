package com.portfolio.model.portfolio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a single intraday data point for the portfolio value chart.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntradayDataPoint {
    
    /** 
     * ISO time string representing the candle time.
     * Example: "09:15", "09:30", "15:30" 
     */
    private String timestamp;
    
    /** 
     * Total portfolio value at this specific candle.
     */
    private double totalWealth;
    
    /** 
     * Absolute change compared to yesterday's EOD close (the 9:15 anchor).
     */
    private double changeFromOpen;
    
    /** 
     * Percentage change compared to yesterday's EOD close.
     */
    private double changeFromOpenPct;
    
    /** 
     * Flag indicating whether this is a live/current point 
     * (the very last candle of the day during market hours).
     */
    private boolean isLive;
    
    /** 
     * Per-portfolio/broker breakdown at this specific time.
     */
    private List<PortfolioIntradayEntry> portfolios;
}
