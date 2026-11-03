package com.portfolio.mapper.holdings;

import org.springframework.stereotype.Component;

import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.portfolio.model.portfolio.EquityHoldings;

@Component
public class EquityHoldingsMapper {
    public EquityHoldings toEquityHoldings(EquityModel equityModel) {
        String normalizedSymbol = com.portfolio.model.util.SymbolResolver.normalize(equityModel.getSymbol());
        boolean inventedCasCost = PortfolioHoldingsMapper.looksLikeInventedCasCost(equityModel);
        Double cost = null;
        if (equityModel.getInvestmentValue() != null && equityModel.getInvestmentValue() > 0) {
            cost = equityModel.getInvestmentValue();
        } else if (!inventedCasCost
                && equityModel.getAvgBuyingPrice() != null
                && equityModel.getQuantity() != null
                && equityModel.getQuantity() > 0) {
            cost = equityModel.getAvgBuyingPrice() * equityModel.getQuantity();
        }
        // null cost = unknown (e.g. CDSL CAS). Never stash 0.0 — that invents +100% returns.
        return EquityHoldings.builder()
            .isin(equityModel.getIsin())
            .symbol(normalizedSymbol)
            .name(equityModel.getName())
            .sector(equityModel.getSector())
            .industry(equityModel.getIndustry())
            .marketCap(equityModel.getMarketCap())
            .quantity(equityModel.getQuantity())
            .investmentCost(cost)
            .currentValue(equityModel.getCurrentValue())
            .currentPrice(equityModel.getCurrentPrice())
            .averageBuyingPrice(inventedCasCost ? null : equityModel.getAvgBuyingPrice())
            .build();
    }
}
