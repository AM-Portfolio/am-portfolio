package com.portfolio.redis.model;

import java.util.List;

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
public class EquityHoldings {
    private String isin;
    private String symbol;
    private String name;
    private Double quantity;
    private Double currentValue;
    private Double investmentValue;
    private Double gainLoss;
    private Double gainLossPercentage;
    private String sector;
    private String marketCap;
    private List<EquityBrokerHolding> brokerPortfolios;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EquityBrokerHolding {
        private BrokerType brokerType;
        private Double quantity;
    }
}
