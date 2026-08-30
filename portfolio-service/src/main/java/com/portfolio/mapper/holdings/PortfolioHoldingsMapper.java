package com.portfolio.mapper.holdings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

import com.am.common.amcommondata.model.HoldingAllocation;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.model.portfolio.PortfolioHoldings;
import com.portfolio.model.portfolio.EquityBrokerHolding;
import com.portfolio.service.basket.AllocationLedgerService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PortfolioHoldingsMapper {
    private final EquityHoldingsMapper equityHoldingsMapper = new EquityHoldingsMapper();
    private final AllocationLedgerService allocationLedgerService;

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
                if (addedQty <= 0) {
                    continue; // Skip zero/negative quantity or closed positions
                }

                double addedCost = (equity.getAvgBuyingPrice() != null)
                    ? equity.getAvgBuyingPrice() * addedQty : 0.0;

                // If this is the first time we're seeing this symbol, create a new holding
                if (!equityHoldingsMap.containsKey(symbol)) {
                    // Create a new holding with the correct initial quantity
                    EquityHoldings holdings = equityHoldingsMapper.toEquityHoldings(equity);

                    // Enrich with portfolio context
                    holdings.setPortfolioId(portfolio.getId() != null ? portfolio.getId().toString() : null);
                    holdings.setPortfolioName(portfolio.getName());
                    applyAllocationFields(holdings, portfolio, equity);

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
                    // Re-apply allocation against merged raw for BROKER books
                    if (PortfolioKind.isBroker(portfolio.getPortfolioKind())) {
                        double alloc = allocatedForIsin(portfolio, equity.getIsin());
                        double raw = mergedQty;
                        existing.setRawQuantity(raw);
                        existing.setAllocatedQuantity(
                                (existing.getAllocatedQuantity() != null ? existing.getAllocatedQuantity() : 0) + alloc);
                        double available = Math.max(0.0, raw - (existing.getAllocatedQuantity() != null
                                ? existing.getAllocatedQuantity() : 0));
                        existing.setAvailableQuantity(available);
                        existing.setAllocationNote(buildAllocationNote(portfolio, equity.getIsin()));
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

    private void applyAllocationFields(EquityHoldings holdings, PortfolioModelV1 portfolio, EquityModel equity) {
        double raw = equity.getQuantity() != null ? equity.getQuantity() : 0.0;
        holdings.setRawQuantity(raw);
        if (PortfolioKind.isBroker(portfolio.getPortfolioKind())) {
            double alloc = allocatedForIsin(portfolio, equity.getIsin());
            holdings.setAllocatedQuantity(alloc);
            double available = Math.max(0.0, raw - alloc);
            holdings.setAvailableQuantity(available);
            holdings.setAllocationNote(buildAllocationNote(portfolio, equity.getIsin()));
            if (available > 0 && equity.getAvgBuyingPrice() != null) {
                holdings.setInvestmentCost(equity.getAvgBuyingPrice() * available);
            }
        } else {
            holdings.setAllocatedQuantity(0.0);
            holdings.setAvailableQuantity(raw);
        }
    }

    private double allocatedForIsin(PortfolioModelV1 portfolio, String isin) {
        if (portfolio.getId() == null || isin == null) {
            return 0.0;
        }
        return allocationLedgerService.sumActiveQuantityByBrokerPortfolioIdAndIsin(portfolio.getId().toString(), isin);
    }

    private String buildAllocationNote(PortfolioModelV1 portfolio, String isin) {
        if (portfolio.getId() == null || isin == null) {
            return null;
        }
        double total = allocatedForIsin(portfolio, isin);
        if (total > 0) {
            return String.format("%.0f allocated to baskets", total);
        }
        return null;
    }
}
