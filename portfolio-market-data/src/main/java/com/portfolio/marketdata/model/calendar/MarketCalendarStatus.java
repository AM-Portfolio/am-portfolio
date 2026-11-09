package com.portfolio.marketdata.model.calendar;

import java.time.Instant;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketCalendarStatus {
    private String exchange;
    private boolean open;
    private String reason;
    private Instant asOf;
    private LocalTime sessionStart;
    private LocalTime sessionEnd;
}
