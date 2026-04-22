package com.am.common.amcommondata.model.asset.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.Builder;
import java.util.List;

import java.util.ArrayList;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MarketDataModel {
    private OHLCVModel latestOHLCV;
    private Double marketPrice;
    private Double dayHigh;
    private Double dayLow;
    private Double fiftyTwoWeekHigh;
    private Double fiftyTwoWeekLow;
    private Double previousClose;
    private Long volume;
    private Double marketCap;
    private String marketCapCategory;
    private Double priceInHomeCurrency;
    
    // Market Indicators
    private Double relativeStrengthIndex;
    private Double movingAverage50;
    private Double movingAverage200;
    private Double averageVolume30Days;
    private Double beta;
    private Double volatility30Days;
    
    // Order Book
    private Double bidPrice;
    private Double askPrice;
    private Long bidSize;
    private Long askSize;
    private Double spreadPercentage;

    @Builder.Default
    private List<TimeSeriesDataModel> timeSeriesData = new ArrayList<>();
}
