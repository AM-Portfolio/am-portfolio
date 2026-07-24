package com.portfolio.model.portfolio.v1;

import java.util.List;
import java.util.Map;

import com.am.common.amcommondata.model.enums.BrokerType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.portfolio.model.portfolio.EquityHoldings;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(Include.NON_NULL)
public class PortfolioSummaryV1 extends BasePortfolioSummay {
    private Map<BrokerType, BrokerPortfolioSummary> brokerPortfolios;
    private Map<String, List<EquityHoldings>> marketCapHoldings;
    private Map<String, List<EquityHoldings>> sectorialHoldings;
    
    public static PortfolioSummaryV1 empty() {
        return PortfolioSummaryV1.builder()
            .investmentValue(0.0)
            .currentValue(0.0)
            .totalGainLoss(0.0)
            .totalGainLossPercentage(0.0)
            .todayGainLoss(0.0)
            .todayGainLossPercentage(0.0)
            .totalAssets(0)
            .gainersCount(0)
            .losersCount(0)
            .todayGainersCount(0)
            .todayLosersCount(0)
            .brokerPortfolios(java.util.Collections.emptyMap())
            .marketCapHoldings(java.util.Collections.emptyMap())
            .sectorialHoldings(java.util.Collections.emptyMap())
            .dataStatus(com.portfolio.model.portfolio.DataStatus.STALE)
            .lastUpdated(java.time.LocalDateTime.now())
            .build();
    }
}
