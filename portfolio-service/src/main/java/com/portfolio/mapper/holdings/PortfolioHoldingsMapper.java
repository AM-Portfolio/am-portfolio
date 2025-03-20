package com.portfolio.mapper.holdings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.model.portfolio.PortfolioHoldings;
import com.portfolio.model.portfolio.EquityBrokerHolding;

@Component
public class PortfolioHoldingsMapper {
    private final EquityHoldingsMapper equityHoldingsMapper = new EquityHoldingsMapper();

    public PortfolioHoldings toPortfolioHoldingsV1(List<PortfolioModelV1> portfolios) {
        Map<String, EquityHoldings> equityHoldingsMap = new HashMap<>();

        for (PortfolioModelV1 portfolio : portfolios) {
            for (EquityModel equity : portfolio.getEquityModels()) {
                String isin = equity.getIsin();

                EquityHoldings holdings = equityHoldingsMap.computeIfAbsent(isin, k -> equityHoldingsMapper.toEquityHoldings(equity));

                holdings.setQuantity(holdings.getQuantity() + equity.getQuantity());

                // Add broker holding
                holdings.getBrokerPortfolios().add(EquityBrokerHolding.builder()
                    .brokerType(portfolio.getBrokerType())
                    .quantity(equity.getQuantity())
                    .build());
            }
        }
        var portfolioHoldings = PortfolioHoldings.builder()
            .equityHoldings(equityHoldingsMap.values().stream().collect(Collectors.toList()))
            .build();
        return portfolioHoldings;
    }
}
