package com.portfolio.analytics.intelligence;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.AssetModel;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.enums.BrokerType;
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
        List<AssetModel> npsModels = new ArrayList<>();

        for (PortfolioModelV1 portfolio : brokers) {
            appendAll(mutualFunds, portfolio.getMutualFunds());
            appendAll(bonds, portfolio.getBonds());
            appendAll(commodities, portfolio.getCommodities());
            appendAll(cash, portfolio.getCash());
            appendAll(npsModels, portfolio.getNpsModels());
            // NPS books sometimes store scheme lines only under equityModels — lift once.
            boolean npsLiftedFromEquities = false;
            if (portfolio.getBrokerType() == BrokerType.NPS
                    && (portfolio.getNpsModels() == null || portfolio.getNpsModels().isEmpty())
                    && portfolio.getEquityModels() != null
                    && !portfolio.getEquityModels().isEmpty()) {
                for (EquityModel eq : portfolio.getEquityModels()) {
                    if (eq == null) {
                        continue;
                    }
                    npsModels.add(copyAsAsset(eq, HealthScoreEngine.ASSET_NPS));
                }
                npsLiftedFromEquities = true;
            }
            if (!npsLiftedFromEquities) {
                mergeEquities(equities, portfolio);
            }
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
                .npsModels(npsModels)
                .build();
    }

    /**
     * Display name → current book value for By-Portfolio X-Ray slices.
     * Prefers {@link PortfolioModelV1#getTotalValue()}; falls back to summing asset current values.
     */
    public static Map<String, Double> brokerBookValues(List<PortfolioModelV1> portfolios) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (PortfolioModelV1 p : filterBrokers(portfolios)) {
            String name = p.getName();
            if (name == null || name.isBlank()) {
                name = p.getBrokerType() != null ? p.getBrokerType().name() : "Unknown";
            }
            double value = p.getTotalValue() != null ? p.getTotalValue() : sumBookValue(p);
            if (value <= 0) {
                continue;
            }
            out.merge(name.trim(), value, Double::sum);
        }
        return out;
    }

    private static double sumBookValue(PortfolioModelV1 p) {
        double sum = 0.0;
        if (p.getEquityModels() != null) {
            for (EquityModel e : p.getEquityModels()) {
                if (e != null && e.getCurrentValue() != null) {
                    sum += e.getCurrentValue();
                }
            }
        }
        for (List<AssetModel> list : List.of(
                p.getMutualFunds(), p.getBonds(), p.getCommodities(), p.getCash(), p.getNpsModels())) {
            if (list == null) {
                continue;
            }
            for (AssetModel a : list) {
                if (a != null && a.getCurrentValue() != null) {
                    sum += a.getCurrentValue();
                }
            }
        }
        return sum;
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
                EquityModel copy = copyEquity(equity, symbol, addedQty, portfolio.getBrokerType());
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
            // Prefer non-equity Class tags when merging the same symbol across books.
            existing.setAssetClass(preferAssetClass(
                    existing.getAssetClass(),
                    resolveEquityAssetClass(equity, portfolio.getBrokerType())));
        }
    }

    private static EquityModel copyEquity(
            EquityModel src, String symbol, double qty, BrokerType bookBroker) {
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
                .assetClass(resolveEquityAssetClass(src, bookBroker))
                .brokerType(src.getBrokerType())
                .status(src.getStatus())
                .build();
    }

    /** Preserve CAS / broker Class tags; NPS book defaults blank rows to NPS. */
    static String resolveEquityAssetClass(EquityModel src, BrokerType bookBroker) {
        String raw = src != null ? src.getAssetClass() : null;
        if (raw != null && !raw.isBlank()) {
            return HealthScoreEngine.normalizeAssetClass(raw);
        }
        if (bookBroker == BrokerType.NPS) {
            return HealthScoreEngine.ASSET_NPS;
        }
        return HealthScoreEngine.ASSET_EQUITY;
    }

    static String preferAssetClass(String a, String b) {
        String na = HealthScoreEngine.normalizeAssetClass(a);
        String nb = HealthScoreEngine.normalizeAssetClass(b);
        if (HealthScoreEngine.ASSET_EQUITY.equals(na) && !HealthScoreEngine.ASSET_EQUITY.equals(nb)) {
            return nb;
        }
        return na;
    }

    private static AssetModel copyAsAsset(EquityModel src, String assetClass) {
        return AssetModel.builder()
                .symbol(src.getSymbol())
                .name(src.getName() != null ? src.getName() : src.getCompanyName())
                .isin(src.getIsin())
                .quantity(src.getQuantity())
                .avgBuyingPrice(src.getAvgBuyingPrice())
                .currentPrice(src.getCurrentPrice())
                .currentValue(src.getCurrentValue())
                .investmentValue(src.getInvestmentValue())
                .assetType(src.getAssetType())
                .assetClass(assetClass)
                .brokerType(src.getBrokerType() != null ? src.getBrokerType() : BrokerType.NPS)
                .build();
    }

    private static void appendAll(List<AssetModel> target, List<? extends AssetModel> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        target.addAll(source);
    }
}
