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
     * Enriches holdings with portfolio context for traceability
     */
    private Map<String, EquityHoldings> processPortfolios(List<PortfolioModelV1> portfolios) {
        Map<String, EquityHoldings> equityHoldingsMap = new HashMap<>();

        for (PortfolioModelV1 portfolio : portfolios) {
            if (portfolio.getEquityModels() == null) continue;

            for (EquityModel equity : portfolio.getEquityModels()) {
                // Use normalized symbol instead of ISIN as the key
                String rawSymbol = equity.getSymbol();
                String symbol = com.portfolio.model.util.SymbolResolver.normalize(rawSymbol);

                if (symbol == null) {
                    continue; // Skip equities without a symbol
                }

                double addedQty = equity.getQuantity() != null ? equity.getQuantity() : 0;
                double addedCost = (equity.getAvgBuyingPrice() != null && equity.getQuantity() != null)
                    ? equity.getAvgBuyingPrice() * equity.getQuantity() : 0.0;

                // If this is the first time we're seeing this symbol, create a new holding
                if (!equityHoldingsMap.containsKey(symbol)) {
                    // Create a new holding with the correct initial quantity
                    EquityHoldings holdings = equityHoldingsMapper.toEquityHoldings(equity);

                    // Enrich with portfolio context
                    holdings.setPortfolioId(portfolio.getId() != null ? portfolio.getId().toString() : null);
                    holdings.setPortfolioName(portfolio.getName());

                    equityHoldingsMap.put(symbol, holdings);
                } else {
                    // Symbol already exists in another broker portfolio - merge quantities and cost
                    EquityHoldings existing = equityHoldingsMap.get(symbol);
                    double mergedQty = (existing.getQuantity() != null ? existing.getQuantity() : 0) + addedQty;
                    double mergedCost = (existing.getInvestmentCost() != null ? existing.getInvestmentCost() : 0) + addedCost;
                    
                    existing.setQuantity(mergedQty);
                    existing.setInvestmentCost(mergedCost);
                    
                    if (mergedQty > 0) {
                        existing.setAverageBuyingPrice(mergedCost / mergedQty);
                    }
                }

                // Add broker holding to the aggregated holding object
                equityHoldingsMap.get(symbol).getBrokerPortfolios().add(EquityBrokerHolding.builder()
                        .brokerType(portfolio.getBrokerType())
                        .quantity(addedQty)
                        .build());
            }
        }

        return equityHoldingsMap;
    }
}
