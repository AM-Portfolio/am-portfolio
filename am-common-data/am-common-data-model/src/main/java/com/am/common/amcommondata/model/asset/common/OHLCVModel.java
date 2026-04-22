package com.am.common.amcommondata.model.asset.common;

import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@Data
@Builder
@JsonInclude(Include.NON_NULL)
public class OHLCVModel {
    private Double open;
    private Double high;
    private Double low;
    private Double close;
    private Double volume;
    private LocalDateTime timestamp;
    private String timeframe;
    private Double adjustedClose;
    private Double dividendAmount;
    private Double splitCoefficient;
}
