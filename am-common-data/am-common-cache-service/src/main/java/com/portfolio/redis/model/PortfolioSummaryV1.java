package com.portfolio.redis.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.am.common.amcommondata.model.enums.BrokerType;
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
public class PortfolioSummaryV1 {
    private Double investmentValue;
    private Double currentValue;
    private Double totalGainLoss;
    private Double totalGainLossPercentage;
    private Integer totalAssets;
    private LocalDateTime lastUpdated;
    private Map<BrokerType, BrokerPortfolioSummary> brokerPortfolios;
    private Map<String, List<EquityHoldings>> marketCapHoldings;
    private Map<String, List<EquityHoldings>> sectorialHoldings;
}
