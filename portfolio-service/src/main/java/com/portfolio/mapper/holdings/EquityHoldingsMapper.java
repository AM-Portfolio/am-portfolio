package com.portfolio.mapper.holdings;

import org.springframework.stereotype.Component;

import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.portfolio.model.portfolio.EquityHoldings;

@Component
public class EquityHoldingsMapper {
    public EquityHoldings toEquityHoldings(EquityModel equityModel) {
        String normalizedSymbol = com.portfolio.model.util.SymbolResolver.normalize(equityModel.getSymbol());
        boolean inventedCasCost = PortfolioHoldingsMapper.looksLikeInventedCasCost(equityModel);
        Double avg = inventedCasCost ? null : equityModel.getAvgBuyingPrice();
        Double qty = equityModel.getQuantity();
        Double investmentCost = null;
        if (!inventedCasCost) {
            if (avg != null && avg > 0 && qty != null) {
                investmentCost = avg * qty;
            } else if (equityModel.getInvestmentValue() != null && equityModel.getInvestmentValue() > 0) {
                investmentCost = equityModel.getInvestmentValue();
                if ((avg == null || avg <= 0) && qty != null && qty > 0) {
                    avg = investmentCost / qty;
                }
            }
        }
        Double importMark = equityModel.getImportMarkPrice();
        if ((importMark == null || importMark <= 0)
                && (avg == null || avg <= 0)
                && equityModel.getCurrentPrice() != null
                && equityModel.getCurrentPrice() > 0) {
            // One-time capture from persisted statement mark before live enrich overwrites currentPrice.
            importMark = equityModel.getCurrentPrice();
        }
        // Never emit 0 cost — null means "unknown".
        return EquityHoldings.builder()
            .isin(equityModel.getIsin())
            .symbol(normalizedSymbol)
            .name(equityModel.getName())
            .sector(equityModel.getSector())
            .industry(equityModel.getIndustry())
            .marketCap(equityModel.getMarketCap())
            .quantity(qty)
            .investmentCost(investmentCost)
            .averageBuyingPrice(avg != null && avg > 0 ? avg : null)
            .importMarkPrice(importMark != null && importMark > 0 ? importMark : null)
            .currentPrice(equityModel.getCurrentPrice() != null && equityModel.getCurrentPrice() > 0
                    ? equityModel.getCurrentPrice() : null)
            .currentValue(equityModel.getCurrentValue() != null && equityModel.getCurrentValue() > 0
                    ? equityModel.getCurrentValue() : null)
            .build();
    }
}
