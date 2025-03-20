package com.portfolio.model.portfolio.v1;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
public class BasePortfolioSummay {

    private Double totalValue;
    private Double totalCost;
    private Double totalGainLoss;
    private Double totalGainLossPercentage;
    private Integer totalAssets;
    private Integer gainersCount;
    private Integer losersCount;
    private LocalDateTime lastUpdated;
}
