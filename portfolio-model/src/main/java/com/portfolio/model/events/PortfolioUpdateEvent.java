package com.portfolio.model.events;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.asset.mutualfund.MutualFundModel;
import com.am.common.amcommondata.model.enums.BrokerType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class PortfolioUpdateEvent {
    private UUID id;
    private BrokerType brokerType;
    private String userId;
    private String portfolioId;

    // Core Data
    private List<EquityModel> equities;
    private List<MutualFundModel> mutualFunds;

    // Summary / Calculation Data
    private Double totalValue;
    private Double totalInvestment;
    private Double totalGainLoss;
    private Double totalGainLossPercentage;
    private Double todayGainLoss;
    private Double todayGainLossPercentage;

    private LocalDateTime timestamp;
}
