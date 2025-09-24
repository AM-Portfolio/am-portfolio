package com.portfolio.marketdata.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request model for OHLC data POST requests.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OhlcDataRequest {
    private String symbols;
    private String timeFrame;
    private boolean refresh;
    @JsonProperty("isIndexSymbol")
    private boolean indexSymbol;
}
