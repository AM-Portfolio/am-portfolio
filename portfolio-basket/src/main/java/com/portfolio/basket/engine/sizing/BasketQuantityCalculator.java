package com.portfolio.basket.engine.sizing;

import com.portfolio.basket.model.BasketOpportunity;
import com.portfolio.basket.model.BasketOpportunity.BasketItem;
import com.portfolio.basket.model.BasketOpportunity.ItemStatus;
import com.portfolio.basket.util.BasketUtils;
import com.portfolio.marketdata.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class BasketQuantityCalculator {

    private final MarketDataService marketDataService;

    public BasketOpportunity calculateBasketQuantities(Double investmentAmount, BasketOpportunity opportunity,
            boolean includeHeld, List<String> excludedSymbols) {
        if (investmentAmount == null || investmentAmount <= 0) {
            return opportunity;
        }

        Set<String> excluded = excludedSymbols != null
                ? new HashSet<>(excludedSymbols) : Collections.emptySet();

        if (opportunity.getComposition() != null) {
            for (BasketItem item : opportunity.getComposition()) {
                if (excluded.contains(item.getStockSymbol())) {
                    item.setStatus(ItemStatus.EXCLUDED);
                    item.setBuyQuantity(0.0);
                    item.setRebalancedWeight(0.0);
                }
            }

            List<BasketItem> activeItems = opportunity.getComposition().stream()
                    .filter(i -> !excluded.contains(i.getStockSymbol()))
                    .collect(Collectors.toList());

            double totalActiveWeight = activeItems.stream()
                    .mapToDouble(i -> i.getEtfWeight() != null ? i.getEtfWeight() : 0.0)
                    .sum();

            if (totalActiveWeight > 0 && Math.abs(totalActiveWeight - 100.0) > 0.5) {
                double multiplier = 100.0 / totalActiveWeight;
                for (BasketItem item : activeItems) {
                    item.setRebalancedWeight(BasketUtils.round(
                            (item.getEtfWeight() != null ? item.getEtfWeight() : 0.0) * multiplier));
                }
            }
        }

        final double sizingAmount = investmentAmount;

        Set<String> symbols = new HashSet<>();
        if (opportunity.getComposition() != null) {
            for (BasketItem item : opportunity.getComposition()) {
                boolean hasLast = item.getLastPrice() != null && item.getLastPrice() > 0;
                if (!hasLast && item.getStockSymbol() != null) {
                    symbols.add(item.getStockSymbol());
                }
                if (item.getUserHoldingSymbol() != null) {
                    if (!hasLast || item.getStatus() == ItemStatus.SUBSTITUTE) {
                        symbols.add(item.getUserHoldingSymbol());
                    }
                }
            }
        }

        if (symbols.isEmpty() && (opportunity.getComposition() == null || opportunity.getComposition().isEmpty())) {
            return opportunity;
        }

        Map<String, Double> prices = new HashMap<>();
        if (opportunity.getComposition() != null) {
            for (BasketItem item : opportunity.getComposition()) {
                if (item.getLastPrice() != null && item.getLastPrice() > 0 && item.getStockSymbol() != null) {
                    prices.put(item.getStockSymbol(), item.getLastPrice());
                }
            }
        }
        if (!symbols.isEmpty()) {
            log.info("Fetching live prices for {} symbols to calculate quantities (gap-fill)", symbols.size());
            try {
                Map<String, Double> fetched = marketDataService.getCurrentPrices(new ArrayList<>(symbols));
                if (fetched != null) {
                    prices.putAll(fetched);
                }
            } catch (Exception e) {
                log.warn("Price fetch failed during calculateQuantities: {}", e.getMessage());
            }
        } else {
            log.info("Skipping market price fetch — all composition lastPrice present");
        }

        Map<String, Double> gapAmounts = new HashMap<>();

        for (BasketItem item : opportunity.getComposition()) {
            if (excluded.contains(item.getStockSymbol())) {
                item.setBuyQuantity(0.0);
                continue;
            }

            Double price = null;
            if (item.getStatus() == ItemStatus.SUBSTITUTE && item.getUserHoldingSymbol() != null) {
                price = prices.get(item.getUserHoldingSymbol());
            } else {
                price = prices.get(item.getStockSymbol());
                if ((price == null || price <= 0) && item.getUserHoldingSymbol() != null) {
                    price = prices.get(item.getUserHoldingSymbol());
                }
            }
            if (price == null || price <= 0) {
                price = item.getLastPrice();
            }
            if (price == null || price <= 0) {
                log.warn("Price not found for {}, defaulting quantity to 0", item.getStockSymbol());
                item.setBuyQuantity(0.0);
                continue;
            }
            item.setLastPrice(price);

            double weight = item.getRebalancedWeight() != null ? item.getRebalancedWeight()
                    : (item.getEtfWeight() != null ? item.getEtfWeight() : 0.0);
            double baseTargetAmount = (weight / 100.0) * sizingAmount;
            int baseTargetQty = BasketUtils.resolveBaseTargetQty(baseTargetAmount, price, weight);
            double heldQty = item.getHeldQuantity() != null ? item.getHeldQuantity() : 0.0;

            if (includeHeld && (item.getStatus() == ItemStatus.HELD || item.getStatus() == ItemStatus.SUBSTITUTE)) {
                double allocatedQty;
                if (Boolean.TRUE.equals(item.getTargetQuantityLocked()) && item.getTargetQuantity() != null) {
                    allocatedQty = Math.min(item.getTargetQuantity(), heldQty);
                } else {
                    allocatedQty = Math.min(heldQty, baseTargetQty);
                }
                item.setTargetQuantity(allocatedQty);
                item.setBuyQuantity(0.0);
                continue;
            }

            if (!includeHeld && item.getStatus() == ItemStatus.HELD) {
                item.setBuyQuantity(0.0);
                item.setTargetQuantity((double) baseTargetQty);
                continue;
            }

            if (item.getStatus() == ItemStatus.MISSING) {
                item.setBuyQuantity(0.0);
                item.setTargetQuantity((double) baseTargetQty);
                if (!includeHeld) {
                    gapAmounts.put(item.getStockSymbol(), baseTargetAmount);
                }
            }
        }

        if (!includeHeld && !gapAmounts.isEmpty()) {
            for (BasketItem item : opportunity.getComposition()) {
                if (!gapAmounts.containsKey(item.getStockSymbol())) {
                    continue;
                }
                if (Boolean.TRUE.equals(item.getTargetQuantityLocked())) {
                    continue;
                }
                Double price = item.getLastPrice();
                Double itemWeight = item.getRebalancedWeight() != null ? item.getRebalancedWeight() : item.getEtfWeight();
                double w = itemWeight != null ? itemWeight : 0.0;
                int buyQty = BasketUtils.resolveBuyQty(gapAmounts.get(item.getStockSymbol()), price, w);
                item.setBuyQuantity((double) buyQty);
            }
        }

        if (opportunity.getComposition() != null) {
            int total = (int) opportunity.getComposition().stream()
                    .filter(i -> i.getStatus() != ItemStatus.EXCLUDED)
                    .count();
            int matchCount = 0;
            double replicaTotal = 0.0;
            for (BasketItem item : opportunity.getComposition()) {
                if (item.getStatus() == ItemStatus.EXCLUDED) {
                    continue;
                }
                double price = item.getLastPrice() != null ? item.getLastPrice() : 0.0;

                if (item.getStatus() == ItemStatus.HELD || item.getStatus() == ItemStatus.SUBSTITUTE) {
                    matchCount++;
                    double allocatedQty = item.getTargetQuantity() != null ? item.getTargetQuantity() : 0.0;
                    double contribution = allocatedQty * price;
                    if (!includeHeld && item.getBuyQuantity() != null && item.getBuyQuantity() > 0) {
                        contribution += item.getBuyQuantity() * price;
                    }
                    double itemWeight = investmentAmount > 0
                            ? BasketUtils.round((contribution / investmentAmount) * 100.0) : 0.0;
                    item.setReplicaWeight(itemWeight);
                    replicaTotal += itemWeight;
                } else if (item.getStatus() == ItemStatus.MISSING) {
                    if (item.getBuyQuantity() != null && item.getBuyQuantity() > 0) {
                        double buyValue = item.getBuyQuantity() * price;
                        double itemWeight = investmentAmount > 0
                                ? BasketUtils.round((buyValue / investmentAmount) * 100.0) : 0.0;
                        item.setReplicaWeight(itemWeight);
                        replicaTotal += itemWeight;
                    } else {
                        item.setReplicaWeight(0.0);
                    }
                } else {
                    item.setReplicaWeight(0.0);
                }
            }
            opportunity.setMatchScore(BasketUtils.round(total == 0 ? 0 : (double) matchCount / total * 100.0));
            opportunity.setReplicaScore(BasketUtils.round(Math.min(replicaTotal, 100.0)));
            opportunity.setReadyToReplicate(replicaTotal >= 90.0);
            opportunity.setHeldCount(matchCount);
            opportunity.setMissingCount(total - matchCount);
        }
        opportunity.setInvestmentAmount(investmentAmount);

        double heldScore = opportunity.getComposition().stream().filter(i -> i.getStatus() == ItemStatus.HELD)
                .mapToDouble(i -> i.getReplicaWeight() != null ? i.getReplicaWeight() : 0.0).sum();
        double subScore = opportunity.getComposition().stream().filter(i -> i.getStatus() == ItemStatus.SUBSTITUTE)
                .mapToDouble(i -> i.getReplicaWeight() != null ? i.getReplicaWeight() : 0.0).sum();
        double missingScore = opportunity.getComposition().stream()
                .filter(i -> i.getStatus() == ItemStatus.MISSING && !excluded.contains(i.getStockSymbol()))
                .mapToDouble(i -> i.getRebalancedWeight() != null ? i.getRebalancedWeight()
                        : (i.getEtfWeight() != null ? i.getEtfWeight() : 0.0))
                .sum();
        opportunity.setHeldMatchScore(BasketUtils.round(heldScore));
        opportunity.setSubstituteMatchScore(BasketUtils.round(subScore));
        opportunity.setMissingMatchScore(BasketUtils.round(missingScore));

        double actualCost = opportunity.getComposition().stream()
                .filter(i -> i.getBuyQuantity() != null && i.getBuyQuantity() > 0
                        && i.getLastPrice() != null)
                .mapToDouble(i -> i.getBuyQuantity() * i.getLastPrice())
                .sum();
        double heldCoverage = opportunity.getComposition().stream()
                .filter(i -> !excluded.contains(i.getStockSymbol()))
                .filter(i -> i.getStatus() == ItemStatus.HELD || i.getStatus() == ItemStatus.SUBSTITUTE)
                .mapToDouble(i -> {
                    double price = i.getLastPrice() != null ? i.getLastPrice() : 0.0;
                    double heldQty = i.getHeldQuantity() != null ? i.getHeldQuantity() : 0.0;
                    double targetQty = i.getTargetQuantity() != null ? i.getTargetQuantity() : 0.0;
                    return Math.min(heldQty, targetQty) * price;
                }).sum();
        opportunity.setActualInvestmentCost(BasketUtils.round(actualCost));
        opportunity.setFreshOrderAmount(BasketUtils.round(actualCost));
        opportunity.setHeldCoverageValue(BasketUtils.round(heldCoverage));
        opportunity.setBudgetVariance(BasketUtils.round(actualCost - investmentAmount));
        if (investmentAmount > 0) {
            opportunity.setBudgetUtilization(BasketUtils.round((heldCoverage + actualCost) / investmentAmount * 100.0));
        }
        opportunity.setExcludedSymbols(new ArrayList<>(excluded));

        return opportunity;
    }
}
