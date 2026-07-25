package com.portfolio.mapper.holdings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.asset.mutualfund.MutualFundModel;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.model.portfolio.MutualFundHoldings;
import com.portfolio.model.portfolio.PortfolioHoldings;
import com.portfolio.model.portfolio.EquityBrokerHolding;

@Component
public class PortfolioHoldingsMapper {
    private final EquityHoldingsMapper equityHoldingsMapper = new EquityHoldingsMapper();
    private final MutualFundHoldingsMapper mutualFundHoldingsMapper = new MutualFundHoldingsMapper();

    public PortfolioHoldings toPortfolioHoldingsV1(List<PortfolioModelV1> portfolios) {
        Map<String, EquityHoldings> equityHoldingsMap = processPortfolios(portfolios);
        Map<String, MutualFundHoldings> mutualFundHoldingsMap = processMutualFunds(portfolios);

        return PortfolioHoldings.builder()
                .equityHoldings(equityHoldingsMap.values().stream().collect(Collectors.toList()))
                .mutualFundHoldings(mutualFundHoldingsMap.values().stream().collect(Collectors.toList()))
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

                // If this is the first time we're seeing this symbol, create a new holding
                if (!equityHoldingsMap.containsKey(symbol)) {
                    // Create a new holding with the correct initial quantity
                    EquityHoldings holdings = equityHoldingsMapper.toEquityHoldings(equity);

                    // Enrich with portfolio context
                    holdings.setPortfolioId(portfolio.getId() != null ? portfolio.getId().toString() : null);
                    holdings.setPortfolioName(portfolio.getName());

                    equityHoldingsMap.put(symbol, holdings);
                } else {
                    // Aggregate quantity and average buying price
                    EquityHoldings existing = equityHoldingsMap.get(symbol);
                    double currentQty = existing.getQuantity() != null ? existing.getQuantity() : 0.0;
                    double currentCost = (existing.getQuantity() != null && existing.getAverageBuyingPrice() != null) 
                            ? (existing.getQuantity() * existing.getAverageBuyingPrice()) : 0.0;
                    
                    double newQty = equity.getQuantity() != null ? equity.getQuantity() : 0.0;
                    double newCost = (equity.getQuantity() != null && equity.getAvgBuyingPrice() != null) 
                            ? (equity.getQuantity() * equity.getAvgBuyingPrice()) : 0.0;
                    
                    double totalQty = currentQty + newQty;
                    double totalCost = currentCost + newCost;
                    
                    existing.setQuantity(totalQty);
                    existing.setInvestmentCost(totalCost);
                    if (totalQty > 0) {
                        existing.setAverageBuyingPrice(totalCost / totalQty);
                    }
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

    private Map<String, MutualFundHoldings> processMutualFunds(List<PortfolioModelV1> portfolios) {
        Map<String, MutualFundHoldings> mutualFundHoldingsMap = new HashMap<>();

        for (PortfolioModelV1 portfolio : portfolios) {
            if (portfolio.getMutualFundModels() == null) continue;
            for (MutualFundModel mf : portfolio.getMutualFundModels()) {
                String symbol = mf.getSymbol();
                if (symbol == null) continue;

                if (!mutualFundHoldingsMap.containsKey(symbol)) {
                    MutualFundHoldings holdings = mutualFundHoldingsMapper.toMutualFundHoldings(mf);
                    holdings.setPortfolioId(portfolio.getId() != null ? portfolio.getId().toString() : null);
                    holdings.setPortfolioName(portfolio.getName());
                    mutualFundHoldingsMap.put(symbol, holdings);
                } else {
                    MutualFundHoldings existing = mutualFundHoldingsMap.get(symbol);
                    double currentQty = existing.getQuantity() != null ? existing.getQuantity() : 0.0;
                    double currentCost = (existing.getQuantity() != null && existing.getAverageBuyingPrice() != null) 
                            ? (existing.getQuantity() * existing.getAverageBuyingPrice()) : 0.0;
                    
                    double newQty = mf.getQuantity() != null ? mf.getQuantity() : 0.0;
                    double newCost = (mf.getQuantity() != null && mf.getAvgBuyingPrice() != null) 
                            ? (mf.getQuantity() * mf.getAvgBuyingPrice()) : 0.0;
                    
                    double totalQty = currentQty + newQty;
                    double totalCost = currentCost + newCost;
                    
                    existing.setQuantity(totalQty);
                    existing.setInvestmentCost(totalCost);
                    if (totalQty > 0) {
                        existing.setAverageBuyingPrice(totalCost / totalQty);
                    }
                }

                MutualFundHoldings holdings = mutualFundHoldingsMap.get(symbol);
                holdings.getBrokerPortfolios().add(EquityBrokerHolding.builder()
                        .brokerType(portfolio.getBrokerType())
                        .quantity(mf.getQuantity())
                        .build());
            }
        }

        return mutualFundHoldingsMap;
    }
}
