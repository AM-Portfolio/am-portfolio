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
        Map<String, EquityHoldings> equityHoldingsMap = processPortfolios(portfolios);
        
        return PortfolioHoldings.builder()
            .equityHoldings(equityHoldingsMap.values().stream().collect(Collectors.toList()))
            .build();
    }

    public List<EquityHoldings> toEquityHoldings(List<PortfolioModelV1> portfolios) {
        Map<String, EquityHoldings> equityHoldingsMap = processPortfolios(portfolios);
        return equityHoldingsMap.values().stream().collect(Collectors.toList());
    }
    
    /**
     * Common method to process portfolios and create equity holdings map
     * Uses symbol as the key for identifying unique holdings
     */
    private Map<String, EquityHoldings> processPortfolios(List<PortfolioModelV1> portfolios) {
        Map<String, EquityHoldings> equityHoldingsMap = new HashMap<>();

        for (PortfolioModelV1 portfolio : portfolios) {
            for (EquityModel equity : portfolio.getEquityModels()) {
                // Use symbol instead of ISIN as the key
                String symbol = equity.getSymbol();
                
                if (symbol == null) {
                    continue; // Skip equities without a symbol
                }

                // If this is the first time we're seeing this symbol, create a new holding
                if (!equityHoldingsMap.containsKey(symbol)) {
                    // Create a new holding with the correct initial quantity
                    EquityHoldings holdings = equityHoldingsMapper.toEquityHoldings(equity);
                    equityHoldingsMap.put(symbol, holdings);
                }
                
                // Get the holdings (either newly created or existing)
                EquityHoldings holdings = equityHoldingsMap.get(symbol);
                
                // Add broker holding
                holdings.getBrokerPortfolios().add(EquityBrokerHolding.builder()
                    .brokerType(portfolio.getBrokerType())
                    .quantity(equity.getQuantity())
                    .build());
            }
        }
        
        return equityHoldingsMap;
    }
}
