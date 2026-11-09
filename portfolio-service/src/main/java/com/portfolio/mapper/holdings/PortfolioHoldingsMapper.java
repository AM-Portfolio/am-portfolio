package com.portfolio.mapper.holdings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.AssetModel;
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

    public static final String ASSET_EQUITY = "EQUITY";
    public static final String ASSET_FIXED_INCOME = "FIXED_INCOME";
    public static final String ASSET_COMMODITY = "COMMODITY";
    public static final String ASSET_CASH = "CASH";

    public PortfolioHoldings toPortfolioHoldingsV1(List<PortfolioModelV1> portfolios) {
        List<EquityHoldings> all = toEquityHoldings(portfolios);
        return PortfolioHoldings.builder()
                .equityHoldings(all)
                .build();
    }

    public List<EquityHoldings> toEquityHoldings(List<PortfolioModelV1> portfolios) {
        Map<String, EquityHoldings> equityHoldingsMap = processPortfolios(portfolios);
        List<EquityHoldings> list = new ArrayList<>(equityHoldingsMap.values());
        for (EquityHoldings h : list) {
            if (h.getAssetClass() == null || h.getAssetClass().isBlank()) {
                h.setAssetClass(ASSET_EQUITY);
            }
        }
        list.addAll(processAssetClassLists(portfolios));
        return list;
    }

    /**
     * Map bonds / commodities / cash into holdings rows (stored value, no live MD).
     */
    List<EquityHoldings> processAssetClassLists(List<PortfolioModelV1> portfolios) {
        List<EquityHoldings> out = new ArrayList<>();
        if (portfolios == null) {
            return out;
        }
        for (PortfolioModelV1 portfolio : portfolios) {
            if (portfolio == null) {
                continue;
            }
            String portfolioId = portfolio.getId() != null ? portfolio.getId().toString() : null;
            String portfolioName = portfolio.getName();
            appendAssets(out, portfolio.getBonds(), ASSET_FIXED_INCOME, portfolioId, portfolioName);
            appendAssets(out, portfolio.getCommodities(), ASSET_COMMODITY, portfolioId, portfolioName);
            appendAssets(out, portfolio.getCash(), ASSET_CASH, portfolioId, portfolioName);
        }
        return out;
    }

    private void appendAssets(
            List<EquityHoldings> out,
            List<AssetModel> assets,
            String assetClass,
            String portfolioId,
            String portfolioName) {
        if (assets == null || assets.isEmpty()) {
            return;
        }
        for (AssetModel asset : assets) {
            if (asset == null) {
                continue;
            }
            EquityHoldings row = toClassHolding(asset, assetClass, portfolioId, portfolioName);
            if (row != null) {
                out.add(row);
            }
        }
    }

    private EquityHoldings toClassHolding(
            AssetModel asset,
            String assetClass,
            String portfolioId,
            String portfolioName) {
        String name = asset.getName() != null ? asset.getName().trim() : "";
        String symbol = asset.getSymbol() != null ? asset.getSymbol().trim() : "";
        if (symbol.isEmpty() && !name.isEmpty()) {
            symbol = name.toUpperCase().replaceAll("\\s+", "_");
        }
        if (symbol.isEmpty() && name.isEmpty()) {
            return null;
        }
        if (name.isEmpty()) {
            name = symbol;
        }

        double qty = asset.getQuantity() != null ? asset.getQuantity() : 0.0;
        double value = 0.0;
        if (asset.getCurrentValue() != null && asset.getCurrentValue() > 0) {
            value = asset.getCurrentValue();
        } else if (qty > 0 && asset.getCurrentPrice() != null && asset.getCurrentPrice() > 0) {
            value = qty * asset.getCurrentPrice();
        } else if (qty > 0 && asset.getAvgBuyingPrice() != null && asset.getAvgBuyingPrice() > 0) {
            value = qty * asset.getAvgBuyingPrice();
        }
        if (value <= 0) {
            return null;
        }

        double avg = asset.getAvgBuyingPrice() != null
                ? asset.getAvgBuyingPrice()
                : (qty > 0 ? value / qty : value);
        double price = asset.getCurrentPrice() != null && asset.getCurrentPrice() > 0
                ? asset.getCurrentPrice()
                : (qty > 0 ? value / qty : value);
        double cost = asset.getInvestmentValue() != null && asset.getInvestmentValue() > 0
                ? asset.getInvestmentValue()
                : (qty > 0 && avg > 0 ? qty * avg : value);

        return EquityHoldings.builder()
                .isin(symbol)
                .symbol(symbol)
                .name(name)
                .sector(assetClass)
                .industry(assetClass)
                .portfolioId(portfolioId)
                .portfolioName(portfolioName)
                .quantity(qty > 0 ? qty : 1.0)
                .investmentCost(cost)
                .currentValue(value)
                .averageBuyingPrice(avg)
                .currentPrice(price)
                .gainLoss(asset.getProfitLoss() != null ? asset.getProfitLoss() : 0.0)
                .gainLossPercentage(
                        asset.getProfitLossPercentage() != null ? asset.getProfitLossPercentage() : 0.0)
                .todayGainLoss(0.0)
                .todayGainLossPercentage(0.0)
                .percentageChange(0.0)
                .assetClass(assetClass)
                .brokerPortfolios(new ArrayList<>())
                .build();
    }

    /**
     * Common method to process portfolios and create equity holdings map
     * Uses symbol as the key for identifying unique holdings
     * Enriches holdings with portfolio context for traceability
     */
    private Map<String, EquityHoldings> processPortfolios(List<PortfolioModelV1> portfolios) {
        Map<String, EquityHoldings> equityHoldingsMap = new HashMap<>();
        // One ledger aggregation per broker portfolio — never N+1 per ISIN.
        Map<String, Map<String, Double>> allocationsByPortfolio = new HashMap<>();
        for (PortfolioModelV1 portfolio : portfolios) {
            if (portfolio.getId() != null && PortfolioKind.isBroker(portfolio.getPortfolioKind())) {
                allocationsByPortfolio.put(
                        portfolio.getId().toString(),
                        allocationLedgerService.getActiveAllocationsMap(portfolio.getId().toString()));
            }
        }

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
                    holdings.setAssetClass(ASSET_EQUITY);
                    applyAllocationFields(holdings, portfolio, equity, allocationsByPortfolio);

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
                        double alloc = allocatedForIsin(portfolio, equity.getIsin(), allocationsByPortfolio);
                        double raw = mergedQty;
                        existing.setRawQuantity(raw);
                        existing.setAllocatedQuantity(
                                (existing.getAllocatedQuantity() != null ? existing.getAllocatedQuantity() : 0) + alloc);
                        double available = Math.max(0.0, raw - (existing.getAllocatedQuantity() != null
                                ? existing.getAllocatedQuantity() : 0));
                        existing.setAvailableQuantity(available);
                        existing.setAllocationNote(buildAllocationNote(alloc));
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

    private void applyAllocationFields(
            EquityHoldings holdings,
            PortfolioModelV1 portfolio,
            EquityModel equity,
            Map<String, Map<String, Double>> allocationsByPortfolio) {
        double raw = equity.getQuantity() != null ? equity.getQuantity() : 0.0;
        holdings.setRawQuantity(raw);
        if (PortfolioKind.isBroker(portfolio.getPortfolioKind())) {
            double alloc = allocatedForIsin(portfolio, equity.getIsin(), allocationsByPortfolio);
            holdings.setAllocatedQuantity(alloc);
            double available = Math.max(0.0, raw - alloc);
            holdings.setAvailableQuantity(available);
            holdings.setAllocationNote(buildAllocationNote(alloc));
            if (available > 0 && equity.getAvgBuyingPrice() != null) {
                holdings.setInvestmentCost(equity.getAvgBuyingPrice() * available);
            }
        } else {
            holdings.setAllocatedQuantity(0.0);
            holdings.setAvailableQuantity(raw);
        }
    }

    private double allocatedForIsin(
            PortfolioModelV1 portfolio,
            String isin,
            Map<String, Map<String, Double>> allocationsByPortfolio) {
        if (portfolio.getId() == null || isin == null) {
            return 0.0;
        }
        Map<String, Double> map = allocationsByPortfolio.get(portfolio.getId().toString());
        if (map == null) {
            return 0.0;
        }
        return map.getOrDefault(isin, 0.0);
    }

    private String buildAllocationNote(double total) {
        if (total > 0) {
            return String.format("%.0f allocated to baskets", total);
        }
        return null;
    }
}
