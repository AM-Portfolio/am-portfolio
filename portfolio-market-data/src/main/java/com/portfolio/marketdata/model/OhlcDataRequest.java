package com.portfolio.marketdata.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.portfolio.model.market.TimeFrame;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request model for OHLC data POST requests.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OhlcDataRequest {
    
    /**
     * List of symbols to get OHLC data for, joined as comma-separated string
     */
    private String symbols;
    
    /**
     * Time frame for the OHLC data
     */
    private String timeFrame;
    
    /**
     * Whether to force refresh the data or use cached data
     */
    private boolean forceRefresh;
    
    /**
     * Whether the symbols are index symbols
     */
    private boolean indexSymbol;
}
