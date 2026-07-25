package com.portfolio.mapper.holdings;

import org.springframework.stereotype.Component;

import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.portfolio.model.portfolio.EquityHoldings;

@Component
public class EquityHoldingsMapper {
    public EquityHoldings toEquityHoldings(EquityModel equityModel) {
        String normalizedSymbol = com.portfolio.model.util.SymbolResolver.normalize(equityModel.getSymbol());
        return EquityHoldings.builder()
            .isin(equityModel.getIsin())
            .symbol(normalizedSymbol)
            .name(equityModel.getName())
            .sector(equityModel.getSector())
            .industry(equityModel.getIndustry())
            .marketCap(equityModel.getMarketCap())
            .quantity(equityModel.getQuantity())
            .investmentCost(equityModel.getAvgBuyingPrice() != null && equityModel.getQuantity() != null ? equityModel.getAvgBuyingPrice() * equityModel.getQuantity() : 0.0)
            .averageBuyingPrice(equityModel.getAvgBuyingPrice())
            .build();
    }
}
