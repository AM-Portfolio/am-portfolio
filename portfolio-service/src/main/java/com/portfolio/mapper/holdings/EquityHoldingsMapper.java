package com.portfolio.mapper.holdings;

import org.springframework.stereotype.Component;

import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.portfolio.model.portfolio.EquityHoldings;

@Component
public class EquityHoldingsMapper {
    public EquityHoldings toEquityHoldings(EquityModel equityModel) {
        return EquityHoldings.builder()
            .isin(equityModel.getIsin())
            .symbol(equityModel.getSymbol())
            .name(equityModel.getName())
            .sector(equityModel.getIndustry())
            .industry(equityModel.getIndustry())
            .marketCap(equityModel.getMarketCap())
            .quantity(equityModel.getQuantity())
            .investmentCost(equityModel.getAvgBuyingPrice() * equityModel.getQuantity())
            .build();
    }
}
