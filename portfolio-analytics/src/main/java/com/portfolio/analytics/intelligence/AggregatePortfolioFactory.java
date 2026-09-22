package com.portfolio.analytics.intelligence;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.AssetModel;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.enums.PortfolioKind;
import com.portfolio.model.util.SymbolResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a synthetic {@link PortfolioModelV1} by merging the owner's BROKER books
 * (same scope as holdings-all). Equities merge by normalized symbol (qty + cost-weighted avg).
 */
@Component
public class AggregatePortfolioFactory {

    /**
     * @param userId owner id stamped on the synthetic model
     * @param portfolios all portfolios for the user (non-broker / baskets skipped)
     */
    public PortfolioModelV1 mergeBrokerBooks(String userId, List<PortfolioModelV1> portfolios) {
        List<PortfolioModelV1> brokers = filterBrokers(portfolios);
        Map<String, EquityModel> equities = new LinkedHashMap<>();
        List<AssetModel> mutualFunds = new ArrayList<>();
        List<AssetModel> bonds = new ArrayList<>();
        List<AssetModel> commodities = new ArrayList<>();
        List<AssetModel> cash = new ArrayList<>();

        for (PortfolioModelV1 portfolio : brokers) {
            mergeEquities(equities, portfolio);
            appendAll(mutualFunds, portfolio.getMutualFunds());
            appendAll(bonds, portfolio.getBonds());
            appendAll(commodities, portfolio.getCommodities());
            appendAll(cash, portfolio.getCash());
        }

        return PortfolioModelV1.builder()
                .id(null)
                .name("All Portfolios")
                .owner(userId)
                .portfolioKind(PortfolioKind.BROKER)
                .equityModels(new ArrayList<>(equities.values()))
                .mutualFunds(mutualFunds)
                .bonds(bonds)
                .commodities(commodities)
                .cash(cash)
                .build();
    }

    static List<PortfolioModelV1> filterBrokers(List<PortfolioModelV1> portfolios) {
        if (portfolios == null || portfolios.isEmpty()) {
            return List.of();
        }
        List<PortfolioModelV1> out = new ArrayList<>();
        for (PortfolioModelV1 p : portfolios) {
            if (p == null) {
                continue;
            }
            if (p.getPortfolioKind() == PortfolioKind.DELETED) {
                continue;
            }
            if (!PortfolioKind.isBroker(p.getPortfolioKind())) {
                continue;
            }
            out.add(p);
        }
        return out;
    }

    private static void mergeEquities(Map<String, EquityModel> bySymbol, PortfolioModelV1 portfolio) {
        if (portfolio.getEquityModels() == null) {
            return;
        }
        for (EquityModel equity : portfolio.getEquityModels()) {
            if (equity == null) {
                continue;
            }
            String symbol = SymbolResolver.normalize(equity.getSymbol());
            if (symbol == null || symbol.isBlank()) {
                continue;
            }
            double addedQty = equity.getQuantity() != null ? equity.getQuantity() : 0.0;
            if (addedQty <= 0) {
                continue;
            }
            double addedCost = (equity.getAvgBuyingPrice() != null)
                    ? equity.getAvgBuyingPrice() * addedQty
                    : 0.0;

            EquityModel existing = bySymbol.get(symbol);
            if (existing == null) {
                EquityModel copy = copyEquity(equity, symbol, addedQty);
                bySymbol.put(symbol, copy);
                continue;
            }
            double mergedQty = (existing.getQuantity() != null ? existing.getQuantity() : 0.0) + addedQty;
            double existingCost = (existing.getAvgBuyingPrice() != null && existing.getQuantity() != null)
                    ? existing.getAvgBuyingPrice() * existing.getQuantity()
                    : 0.0;
            double mergedCost = existingCost + addedCost;
            existing.setQuantity(mergedQty);
            if (mergedQty > 0 && mergedCost > 0) {
                existing.setAvgBuyingPrice(mergedCost / mergedQty);
            }
            if (existing.getCurrentValue() != null || equity.getCurrentValue() != null) {
                double a = existing.getCurrentValue() != null ? existing.getCurrentValue() : 0.0;
                double b = equity.getCurrentValue() != null ? equity.getCurrentValue() : 0.0;
                existing.setCurrentValue(a + b);
            }
        }
    }

    private static EquityModel copyEquity(EquityModel src, String symbol, double qty) {
        return EquityModel.builder()
                .symbol(symbol)
                .name(src.getName())
                .isin(src.getIsin())
                .companyName(src.getCompanyName())
                .sector(src.getSector())
                .industry(src.getIndustry())
                .marketCap(src.getMarketCap())
                .exchange(src.getExchange())
                .quantity(qty)
                .avgBuyingPrice(src.getAvgBuyingPrice())
                .currentPrice(src.getCurrentPrice())
                .currentValue(src.getCurrentValue())
                .investmentValue(src.getInvestmentValue())
                .assetType(src.getAssetType())
                .brokerType(src.getBrokerType())
                .status(src.getStatus())
                .build();
    }

    private static void appendAll(List<AssetModel> target, List<? extends AssetModel> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        target.addAll(source);
    }
}
