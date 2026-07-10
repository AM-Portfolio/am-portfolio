package com.portfolio.marketdata.model;

import java.time.LocalDate;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;
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
    private String fromDate;
    @JsonProperty("to")
    private String toDate;
    @JsonProperty("interval")
    private String interval;
    private String instrumentType;
    private String filterType;
    private Integer filterFrequency;
    private Boolean continuous;
    private Boolean forceRefresh = false;
    private Map<String, String> additionalParams;
}
