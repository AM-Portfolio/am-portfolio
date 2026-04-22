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

    private Double investmentValue;
    private Double currentValue;
    
    // Overall performance metrics
    private Double totalGainLoss;
    private Double totalGainLossPercentage;
    
    // Today's performance metrics
    private Double todayGainLoss;
    private Double todayGainLossPercentage;
    
    // Portfolio statistics
    private Integer totalAssets;
    private Integer gainersCount;
    private Integer losersCount;
    
    // Day trading statistics
    private Integer todayGainersCount;
    private Integer todayLosersCount;
    
    private LocalDateTime lastUpdated;
}
