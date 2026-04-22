package com.portfolio.redis.model;

import java.time.LocalDateTime;
import java.util.List;

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
public class PortfolioHoldings {
    private List<EquityHoldings> equityHoldings;
    private LocalDateTime lastUpdated;
    private Double totalValue;
    private Double totalGainLoss;
    private Double totalGainLossPercentage;
}
