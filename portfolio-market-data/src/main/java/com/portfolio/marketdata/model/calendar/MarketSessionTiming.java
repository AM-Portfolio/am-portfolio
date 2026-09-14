package com.portfolio.marketdata.model.calendar;

import java.time.LocalDate;
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
public class MarketSessionTiming {
    private String exchange;
    private LocalDate date;
    private boolean open;
    private LocalTime sessionStart;
    private LocalTime sessionEnd;
    private String reason;
}
