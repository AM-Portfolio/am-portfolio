package com.portfolio.marketdata.model;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Local representation of the HistoricalDataResponseV1 returned by am-market's
 * historical-charts endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HistoricalChartsResponse {
    private Map<String, HistoricalData> data;
    private String error;
    private String message;
}
