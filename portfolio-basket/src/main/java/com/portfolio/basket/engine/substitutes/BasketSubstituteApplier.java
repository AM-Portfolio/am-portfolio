package com.portfolio.basket.engine.substitutes;

import com.portfolio.basket.engine.overlap.BasketOverlapCalculator;
import com.portfolio.basket.engine.overlap.SectorProfile;
import com.portfolio.basket.model.BasketOpportunity;
import com.portfolio.basket.model.BasketOpportunity.BasketItem;
import com.portfolio.basket.model.BasketOpportunity.ItemStatus;
import com.portfolio.basket.model.EtfData;
import com.portfolio.basket.model.SubstituteAssignment;
import com.portfolio.basket.service.EnrichedEtfService;
import com.portfolio.basket.util.BasketUtils;
import com.portfolio.basket.util.SectorNormalizer;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.portfolio.EquityHoldings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class BasketSubstituteApplier {

    private final MarketDataService marketDataService;
    private final EnrichedEtfService enrichedEtfService;
    private final BasketOverlapCalculator overlapCalculator;

    public BasketOpportunity applySubstitutesOnExisting(BasketOpportunity base, List<EquityHoldings> userHoldings,
            List<SubstituteAssignment> assignments) {
        if (assignments == null || assignments.isEmpty() || base.getComposition() == null) {
            return base;
        }

        enrichBasketProfile(base);

        Map<String, EquityHoldings> byIsin = userHoldings.stream()
                .filter(h -> h.getIsin() != null)
                .collect(Collectors.toMap(EquityHoldings::getIsin, h -> h, (a, b) -> a));

        Map<String, EquityHoldings> bySymbol = userHoldings.stream()
                .filter(h -> h.getSymbol() != null)
                .collect(Collectors.toMap(h -> h.getSymbol().toUpperCase(Locale.ROOT), h -> h, (a, b) -> a));

        Map<String, Double> consumedWeightByIsin = new HashMap<>();
        for (BasketItem item : base.getComposition()) {
            if ((item.getStatus() == ItemStatus.HELD || item.getStatus() == ItemStatus.SUBSTITUTE)
                    && item.getUserHoldingIsin() != null) {
                consumedWeightByIsin.merge(item.getUserHoldingIsin(),
                        item.getReplicaWeight() != null ? item.getReplicaWeight() : 0.0, Double::sum);
            }
        }

        Set<String> subSymbols = new HashSet<>();
        for (SubstituteAssignment assignment : assignments) {
            EquityHoldings sub = resolveSubstituteHolding(assignment, byIsin, bySymbol);
            if (sub != null && sub.getSymbol() != null) {
                subSymbols.add(sub.getSymbol());
            }
        }
        Map<String, Double> prices = subSymbols.isEmpty() ? Collections.emptyMap()
                : marketDataService.getCurrentPrices(new ArrayList<>(subSymbols));

        Set<String> revertedAutoSubstituteSlots = new HashSet<>();
        planReclaimForExplicitAssignments(base, assignments, byIsin, bySymbol, consumedWeightByIsin,
                revertedAutoSubstituteSlots);

        Map<String, List<SubstituteAssignment>> assignmentsByMissing = assignments.stream()
                .filter(a -> a.getMissingIsin() != null && !a.getMissingIsin().isBlank())
                .collect(Collectors.groupingBy(SubstituteAssignment::getMissingIsin));

        List<String> warnings = new ArrayList<>();
        List<BasketItem> newComposition = new ArrayList<>();
        int appliedCount = 0;
        boolean sectorial = Boolean.TRUE.equals(base.getSectorialBasket());
        Set<String> constituentIsins = base.getEtfConstituentIsins() != null
                ? new HashSet<>(base.getEtfConstituentIsins())
                : Collections.emptySet();

        for (BasketItem originalItem : base.getComposition()) {
            List<SubstituteAssignment> itemAssignments = assignmentsByMissing.get(originalItem.getIsin());
            if (itemAssignments == null) {
                itemAssignments = assignmentsByMissing.get(originalItem.getStockSymbol());
            }

            if (itemAssignments == null || itemAssignments.isEmpty()) {
                if (originalItem.getStatus() == ItemStatus.SUBSTITUTE
                        && revertedAutoSubstituteSlots.contains(slotKey(originalItem))) {
                    newComposition.add(revertSubstituteToMissing(originalItem));
                    continue;
                }
                newComposition.add(originalItem);
                continue;
            }

            double remainingEtfWeight = originalItem.getEtfWeight() != null ? originalItem.getEtfWeight() : 0.0;

            if (originalItem.getStatus() == ItemStatus.HELD) {
                double existingReplica = originalItem.getReplicaWeight() != null ? originalItem.getReplicaWeight() : 0.0;
                if (existingReplica > 0 && remainingEtfWeight > existingReplica) {
                    BasketItem preservedPart = originalItem.toBuilder()
                            .etfWeight(existingReplica)
                            .build();
                    newComposition.add(preservedPart);
                    remainingEtfWeight -= existingReplica;
                } else {
                    newComposition.add(originalItem);
                    continue;
                }
            } else if (originalItem.getStatus() == ItemStatus.SUBSTITUTE) {
                if (originalItem.getUserHoldingIsin() != null) {
                    consumedWeightByIsin.computeIfPresent(originalItem.getUserHoldingIsin(),
                            (k, v) -> Math.max(0, v - (originalItem.getReplicaWeight() != null
                                    ? originalItem.getReplicaWeight() : 0.0)));
                }
            }

            for (SubstituteAssignment assignment : itemAssignments) {
                if (remainingEtfWeight <= 0.01) {
                    break;
                }

                EquityHoldings sub = resolveSubstituteHolding(assignment, byIsin, bySymbol);
                if (sub == null) {
                    String key = firstNonBlank(assignment.getSubstituteIsin(), assignment.getSubstituteSymbol());
                    if (key == null || key.isBlank()) {
                        warnings.add("Missing substitute ISIN/symbol for " + originalItem.getStockSymbol());
                    } else {
                        warnings.add("Substitute not in holdings: " + key);
                    }
                    continue;
                }

                if (sectorial) {
                    String missingSector = SectorNormalizer.normalizeFine(originalItem.getSector());
                    String subSector = SectorNormalizer.normalizeFine(sub.getSector());
                    if (!sectorsMatch(missingSector, subSector)) {
                        warnings.add("Sector mismatch for " + sub.getSymbol() + ": expected " + missingSector);
                        continue;
                    }
                } else if (!constituentIsins.isEmpty() && sub.getIsin() != null
                        && !constituentIsins.contains(sub.getIsin())) {
                    warnings.add("Not an index constituent: " + sub.getSymbol());
                }

                String resolvedIsin = sub.getIsin() != null ? sub.getIsin()
                        : firstNonBlank(assignment.getSubstituteIsin(), assignment.getSubstituteSymbol());
                double subWeight = overlapCalculator.getAvailableWeight(sub);

                double consumed = consumedWeightByIsin.getOrDefault(resolvedIsin, 0.0);
                double availableSubWeight = subWeight - consumed;

                if (availableSubWeight < 0.01) {
                    log.warn("Substitute ISIN fully consumed: {}", resolvedIsin);
                    warnings.add("Substitute fully consumed: " + sub.getSymbol());
                    continue;
                }

                double assignedWeight = assignment.getAssignedWeight() != null ? assignment.getAssignedWeight()
                        : availableSubWeight;
                double matchWeight = Math.min(assignedWeight, availableSubWeight);
                matchWeight = Math.min(matchWeight, remainingEtfWeight);

                if (matchWeight < 0.01) {
                    continue;
                }

                BasketItem splitItem = BasketItem.builder()
                        .stockSymbol(originalItem.getStockSymbol())
                        .isin(originalItem.getIsin())
                        .sector(originalItem.getSector())
                        .status(ItemStatus.SUBSTITUTE)
                        .userHoldingSymbol(sub.getSymbol())
                        .userHoldingIsin(sub.getIsin())
                        .reason("User swap: " + sub.getSymbol())
                        .etfWeight(matchWeight)
                        .userWeight(BasketUtils.round(overlapCalculator.getAvailableWeight(sub)))
                        .marketCapCategory(originalItem.getMarketCapCategory())
                        .marketCapValue(originalItem.getMarketCapValue())
                        .targetQuantity(originalItem.getTargetQuantity())
                        .alternatives(originalItem.getAlternatives())
                        .build();

                consumedWeightByIsin.merge(resolvedIsin, matchWeight, Double::sum);

                double physicalWeight = sub.getWeightInPortfolio() != null ? sub.getWeightInPortfolio() : 0.0;
                double physicalQty = sub.getQuantity() != null ? sub.getQuantity() : 0.0;
                double allocatedQty = (physicalWeight > 0) ? (matchWeight / physicalWeight) * physicalQty
                        : (sub.getAvailableQuantity() != null ? sub.getAvailableQuantity() : 0.0);
                splitItem.setHeldQuantity(BasketUtils.round(allocatedQty));

                splitItem.setHeldAveragePrice(sub.getAverageBuyingPrice());
                splitItem.setReplicaWeight(BasketUtils.round(matchWeight));

                Double subPrice = prices.get(sub.getSymbol());
                if (subPrice == null || subPrice <= 0) {
                    subPrice = (sub.getCurrentPrice() != null && sub.getCurrentPrice() > 0)
                            ? sub.getCurrentPrice()
                            : sub.getAverageBuyingPrice();
                }
                if (subPrice != null && subPrice > 0) {
                    splitItem.setLastPrice(subPrice);
                }

                newComposition.add(splitItem);
                remainingEtfWeight -= matchWeight;
                appliedCount++;
            }

            if (remainingEtfWeight > 0.01) {
                BasketItem remainingItem = BasketItem.builder()
                        .stockSymbol(originalItem.getStockSymbol())
                        .isin(originalItem.getIsin())
                        .sector(originalItem.getSector())
                        .status(ItemStatus.MISSING)
                        .reason(originalItem.getReason())
                        .etfWeight(remainingEtfWeight)
                        .marketCapCategory(originalItem.getMarketCapCategory())
                        .marketCapValue(originalItem.getMarketCapValue())
                        .targetQuantity(originalItem.getTargetQuantity())
                        .alternatives(originalItem.getAlternatives())
                        .build();
                newComposition.add(remainingItem);
            }
        }

        base.setComposition(newComposition);
        base.setAppliedSubstituteCount(appliedCount);
        base.setSubstituteWarnings(warnings.isEmpty() ? null : warnings);

        if (appliedCount == 0) {
            String detail = warnings.isEmpty() ? "Check holdings and sector rules." : String.join("; ", warnings);
            throw new IllegalStateException("No substitutes applied. " + detail);
        }

        Map<String, List<EquityHoldings>> userSectorMap = userHoldings.stream()
                .filter(h -> h.getSector() != null && !SectorNormalizer.isUnknown(h.getSector()))
                .filter(h -> h.getAvailableQuantity() == null || h.getAvailableQuantity() > 0)
                .collect(Collectors.groupingBy(h -> SectorNormalizer.normalizeFine(h.getSector())));
        Map<String, Double> finalConsumed = new HashMap<>();
        for (BasketItem item : newComposition) {
            if ((item.getStatus() == ItemStatus.HELD || item.getStatus() == ItemStatus.SUBSTITUTE)
                    && item.getUserHoldingIsin() != null) {
                finalConsumed.merge(item.getUserHoldingIsin(),
                        item.getReplicaWeight() != null ? item.getReplicaWeight() : 0.0, Double::sum);
            }
        }
        SectorProfile sectorProfile = new SectorProfile(
                Boolean.TRUE.equals(base.getSectorialBasket()),
                base.getDominantSector(),
                base.getEtfConstituentIsins());
        overlapCalculator.refreshMissingAlternatives(newComposition, finalConsumed, userHoldings, userSectorMap, prices,
                sectorProfile);

        int total = base.getComposition().size();
        int matchCount = 0;
        int heldCount = 0;
        int subCount = 0;
        double replica = 0;
        List<BasketItem> buyList = new ArrayList<>();
        for (BasketItem item : base.getComposition()) {
            if (item.getStatus() == ItemStatus.HELD) {
                matchCount++;
                heldCount++;
                replica += item.getReplicaWeight() != null ? item.getReplicaWeight() : 0;
            } else if (item.getStatus() == ItemStatus.SUBSTITUTE) {
                matchCount++;
                subCount++;
                replica += item.getReplicaWeight() != null ? item.getReplicaWeight() : 0;
            } else {
                buyList.add(item);
            }
        }

        base.setMatchScore(BasketUtils.round(total == 0 ? 0 : (double) matchCount / total * 100.0));
        base.setHeldMatchScore(BasketUtils.round(total == 0 ? 0 : (double) heldCount / total * 100.0));
        base.setSubstituteMatchScore(BasketUtils.round(total == 0 ? 0 : (double) subCount / total * 100.0));
        base.setReplicaScore(Math.min(100.0, BasketUtils.round(replica)));
        base.setReadyToReplicate(replica >= 90.0);
        base.setHeldCount(heldCount);
        base.setMissingCount(total - matchCount);
        base.setTotalItems(total);
        base.setBuyList(buyList);

        return base;
    }

    private void enrichBasketProfile(BasketOpportunity base) {
        if (base.getSectorialBasket() != null && base.getEtfConstituentIsins() != null) {
            return;
        }
        if (base.getEtfIsin() == null || base.getEtfIsin().isBlank()) {
            return;
        }
        EtfData etf = enrichedEtfService.getEnrichedEtf(base.getEtfIsin());
        if (etf == null) {
            return;
        }
        SectorProfile profile = overlapCalculator.detectSectorProfile(etf);
        if (base.getSectorialBasket() == null) {
            base.setSectorialBasket(profile.sectorial);
        }
        if (base.getDominantSector() == null) {
            base.setDominantSector(profile.dominantSector);
        }
        if (base.getEtfConstituentIsins() == null) {
            base.setEtfConstituentIsins(profile.constituentIsins);
        }
    }

    private void planReclaimForExplicitAssignments(
            BasketOpportunity base,
            List<SubstituteAssignment> assignments,
            Map<String, EquityHoldings> byIsin,
            Map<String, EquityHoldings> bySymbol,
            Map<String, Double> consumedWeightByIsin,
            Set<String> revertedAutoSubstituteSlots) {
        if (base.getComposition() == null) {
            return;
        }

        Map<String, BasketItem> missingByKey = new HashMap<>();
        for (BasketItem item : base.getComposition()) {
            if (item.getStatus() != ItemStatus.MISSING) {
                continue;
            }
            if (item.getIsin() != null) {
                missingByKey.put(item.getIsin(), item);
            }
            if (item.getStockSymbol() != null) {
                missingByKey.put(item.getStockSymbol(), item);
            }
        }

        for (SubstituteAssignment assignment : assignments) {
            String missingKey = assignment.getMissingIsin();
            if (missingKey == null || missingKey.isBlank()) {
                continue;
            }
            String substituteKey = firstNonBlank(assignment.getSubstituteIsin(), assignment.getSubstituteSymbol());
            if (substituteKey == null || substituteKey.isBlank()) {
                continue;
            }
            BasketItem missingItem = missingByKey.get(missingKey);
            if (missingItem == null) {
                continue;
            }

            EquityHoldings sub = resolveSubstituteHolding(assignment, byIsin, bySymbol);
            if (sub == null) {
                continue;
            }

            String subIsin = sub.getIsin() != null ? sub.getIsin() : substituteKey;
            double neededWeight = assignment.getAssignedWeight() != null
                    ? assignment.getAssignedWeight()
                    : (missingItem.getEtfWeight() != null ? missingItem.getEtfWeight() : 0.0);
            double totalWeight = overlapCalculator.getAvailableWeight(sub);
            double available = totalWeight - consumedWeightByIsin.getOrDefault(subIsin, 0.0);
            if (available >= neededWeight - 0.01) {
                continue;
            }

            double stillNeed = neededWeight - Math.max(0.0, available);
            List<BasketItem> candidates = base.getComposition().stream()
                    .filter(i -> i.getStatus() == ItemStatus.SUBSTITUTE)
                    .filter(i -> subIsin.equals(i.getUserHoldingIsin()))
                    .filter(this::isAutoSubstitute)
                    .filter(i -> !isSameEtfSlot(i, missingItem))
                    .sorted(Comparator.comparingDouble(i -> i.getReplicaWeight() != null ? i.getReplicaWeight() : 0.0))
                    .collect(Collectors.toList());

            for (BasketItem candidate : candidates) {
                if (stillNeed <= 0.01) {
                    break;
                }
                String candidateSlot = slotKey(candidate);
                if (revertedAutoSubstituteSlots.contains(candidateSlot)) {
                    continue;
                }
                double replica = candidate.getReplicaWeight() != null ? candidate.getReplicaWeight() : 0.0;
                if (replica < 0.01) {
                    continue;
                }
                revertedAutoSubstituteSlots.add(candidateSlot);
                consumedWeightByIsin.merge(subIsin, -replica, Double::sum);
                stillNeed -= replica;
                log.info("Reclaiming auto-sub {} ({}) for explicit assign to {}",
                        candidate.getStockSymbol(), replica, missingItem.getStockSymbol());
            }
        }
    }

    private String slotKey(BasketItem item) {
        if (item.getIsin() != null && !item.getIsin().isBlank()) {
            return item.getIsin();
        }
        return item.getStockSymbol();
    }

    private boolean isAutoSubstitute(BasketItem item) {
        String reason = item.getReason();
        return reason == null || reason.startsWith("Substitute:");
    }

    private boolean isSameEtfSlot(BasketItem a, BasketItem b) {
        if (a.getIsin() != null && a.getIsin().equals(b.getIsin())) {
            return true;
        }
        return a.getStockSymbol() != null && a.getStockSymbol().equals(b.getStockSymbol());
    }

    private BasketItem revertSubstituteToMissing(BasketItem subItem) {
        return subItem.toBuilder()
                .status(ItemStatus.MISSING)
                .userHoldingSymbol(null)
                .userHoldingIsin(null)
                .heldQuantity(null)
                .heldAveragePrice(null)
                .replicaWeight(0.0)
                .userWeight(0.0)
                .build();
    }

    private static boolean sectorsMatch(String missingSector, String substituteSector) {
        if (missingSector == null || substituteSector == null) {
            return false;
        }
        if (missingSector.equals(substituteSector)) {
            return true;
        }
        if (SectorNormalizer.isUnknown(missingSector) || SectorNormalizer.isUnknown(substituteSector)) {
            return SectorNormalizer.normalize(missingSector).equals(SectorNormalizer.normalize(substituteSector));
        }
        return SectorNormalizer.normalize(missingSector).equals(SectorNormalizer.normalize(substituteSector));
    }

    private static EquityHoldings resolveSubstituteHolding(
            SubstituteAssignment assignment,
            Map<String, EquityHoldings> byIsin,
            Map<String, EquityHoldings> bySymbol) {
        if (assignment == null) {
            return null;
        }
        String isinOrKey = assignment.getSubstituteIsin();
        if (isinOrKey != null && !isinOrKey.isBlank()) {
            EquityHoldings byKey = byIsin.get(isinOrKey);
            if (byKey != null) {
                return byKey;
            }
            byKey = bySymbol.get(isinOrKey.toUpperCase(Locale.ROOT));
            if (byKey != null) {
                return byKey;
            }
        }
        String symbol = assignment.getSubstituteSymbol();
        if (symbol != null && !symbol.isBlank()) {
            return bySymbol.get(symbol.toUpperCase(Locale.ROOT));
        }
        return null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
