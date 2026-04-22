package com.portfolio.redis.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
public class BrokerPortfolioSummary {
    private Double investmentValue;
    private Double currentValue;
    private Double totalGainLoss;
    private Double totalGainLossPercentage;
    private Integer totalAssets;
    private LocalDateTime lastUpdated;
}
