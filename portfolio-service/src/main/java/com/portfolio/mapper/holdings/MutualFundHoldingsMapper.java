package com.portfolio.mapper.holdings;

import org.springframework.stereotype.Component;

import com.am.common.amcommondata.model.asset.mutualfund.MutualFundModel;
import com.portfolio.model.portfolio.MutualFundHoldings;

@Component
public class MutualFundHoldingsMapper {

    public MutualFundHoldings toMutualFundHoldings(MutualFundModel model) {
        if (model == null) {
            return null;
        }

        return MutualFundHoldings.builder()
                .isin(model.getIsin())
                .symbol(model.getSymbol())
                .name(model.getName())
                .category(model.getCategory())
                .subCategory(model.getSubCategory())
                .fundHouse(model.getFundHouse())
                .quantity(model.getQuantity())
                .averageBuyingPrice(model.getAvgBuyingPrice())
                .currentNav(model.getCurrentPrice()) // assuming currentPrice is mapped from NAV
                .investmentCost(calculateCost(model))
                .currentValue(calculateValue(model))
                .build();
    }

    private Double calculateCost(MutualFundModel model) {
        if (model.getQuantity() != null && model.getAvgBuyingPrice() != null) {
            return model.getQuantity() * model.getAvgBuyingPrice();
        }
        return 0.0;
    }

    private Double calculateValue(MutualFundModel model) {
        if (model.getQuantity() != null && model.getCurrentPrice() != null) {
            return model.getQuantity() * model.getCurrentPrice();
        }
        return 0.0;
    }
}
