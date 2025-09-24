package com.portfolio.marketdata.model;

import java.time.LocalDate;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoricalDataRequest {
    private String symbols;
    @JsonProperty("from")
    private LocalDate fromDate;
    @JsonProperty("to")
    private LocalDate toDate;
    @JsonProperty("interval")
    private String interval;
    private String instrumentType;
    private String filterType;
    private Integer filterFrequency;
    private Boolean continuous;
    private Boolean forceRefresh = false;
    private Map<String, String> additionalParams;
}
