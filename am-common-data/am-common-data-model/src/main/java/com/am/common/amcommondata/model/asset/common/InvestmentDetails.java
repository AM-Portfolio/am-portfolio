package com.am.common.amcommondata.model.asset.common;

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
public class InvestmentDetails {
    private Double investedAmount;
    private Double currentValue;
    private Double profitLoss;
    private Double profitLossPercentage;
    private String currency;
}
