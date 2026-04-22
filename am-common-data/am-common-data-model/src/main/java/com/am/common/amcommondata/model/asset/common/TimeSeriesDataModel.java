package com.am.common.amcommondata.model.asset.common;

import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@Data
@Builder
@JsonInclude(Include.NON_NULL)
public class TimeSeriesDataModel {
    private UUID id;
    private LocalDateTime timestamp;
    private String timeframe;
    private OHLCVModel ohlcv;
    private OHLCVModel latestFiveMin;
    private OHLCVModel latestFifteenMin;
    private OHLCVModel latestHourly;
    private OHLCVModel latestDaily;
    private OHLCVModel latestWeekly;
    private OHLCVModel latestMonthly;
    private OHLCVModel latestYearly;
    private Double volume;
    private Double openInterest;
    private Double volatility;
    private Double relativeStrengthIndex;
    private Double movingAverage50;
    private Double movingAverage200;
    private Double bollingerUpperBand;
    private Double bollingerLowerBand;
    private Double macdLine;
    private Double macdSignalLine;
    private Double macdHistogram;
}
