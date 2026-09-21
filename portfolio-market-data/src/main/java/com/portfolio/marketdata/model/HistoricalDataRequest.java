package com.portfolio.marketdata.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for am-market {@code POST /v1/market-data/historical-data}.
 * Java fields stay {@code fromDate}/{@code toDate} for callers; JSON must be {@code from}/{@code to}.
 * Nulls must be omitted — market NPE when e.g. {@code filterType:null} is present.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HistoricalDataRequest {
    private String symbols;

    private String fromDate;
    private String toDate;

    @JsonProperty("interval")
    private String interval;

    private String instrumentType;
    private String filterType;
    private Integer filterFrequency;
    private Boolean continuous;
    @Builder.Default
    private Boolean forceRefresh = false;
    private Map<String, String> additionalParams;

    @JsonProperty("from")
    public String getFromDate() {
        return fromDate;
    }

    @JsonProperty("from")
    public void setFromDate(String fromDate) {
        this.fromDate = fromDate;
    }

    @JsonProperty("to")
    public String getToDate() {
        return toDate;
    }

    @JsonProperty("to")
    public void setToDate(String toDate) {
        this.toDate = toDate;
    }
}
