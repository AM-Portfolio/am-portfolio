package com.portfolio.mapper.holdings;

import org.springframework.stereotype.Component;

import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.portfolio.model.portfolio.EquityHoldings;

@Component
public class EquityHoldingsMapper {
    public EquityHoldings toEquityHoldings(EquityModel equityModel) {
        String normalizedSymbol = com.portfolio.model.util.SymbolResolver.normalize(equityModel.getSymbol());
        Double qty = equityModel.getQuantity();
        Double avg = equityModel.getAvgBuyingPrice();
        Double investmentValue = equityModel.getInvestmentValue();
        double investmentCost = 0.0;
        Double resolvedAvg = avg;
        if (avg != null && avg > 0 && qty != null && qty > 0) {
            investmentCost = avg * qty;
        } else if (investmentValue != null && investmentValue > 0 && qty != null && qty > 0) {
            // Gmail/Dhan (and others) may persist invested amount without avg cost.
            investmentCost = investmentValue;
            resolvedAvg = investmentValue / qty;
        } else if (avg != null && qty != null && qty > 0) {
            investmentCost = avg * qty;
        }
        return EquityHoldings.builder()
            .isin(equityModel.getIsin())
            .symbol(normalizedSymbol)
            .name(equityModel.getName())
            .sector(equityModel.getSector())
            .industry(equityModel.getIndustry())
            .marketCap(equityModel.getMarketCap())
            .quantity(qty)
            .investmentCost(investmentCost)
            .averageBuyingPrice(resolvedAvg)
            .build();
    }
}
