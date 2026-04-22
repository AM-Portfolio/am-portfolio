package com.am.common.amcommondata.model.asset.equity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FundamentalData {
    private Double eps;
    private Double peRatio;
    private Double pbRatio;
    private Double marketCap;
    private Double bookValue;
    private Double dividendYield;
    private Double roe;
    private Double roa;
    private Double debtToEquity;
    private Double currentRatio;
    private Double quickRatio;
    private Double operatingMargin;
    private Double netProfitMargin;
    private Double freeCashFlow;
    private Double ebitda;
}
