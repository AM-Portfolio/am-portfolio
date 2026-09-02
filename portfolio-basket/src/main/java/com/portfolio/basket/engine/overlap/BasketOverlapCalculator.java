package com.portfolio.basket.engine.overlap;

import com.portfolio.basket.kernel.BasketPriceResolver;
import com.portfolio.basket.model.BasketOpportunity;
import com.portfolio.basket.model.BasketOpportunity.BasketItem;
import com.portfolio.basket.model.BasketOpportunity.ItemStatus;
import com.portfolio.basket.model.EtfData;
import com.portfolio.basket.model.EtfHolding;
import com.portfolio.basket.util.BasketUtils;
import com.portfolio.basket.util.SectorNormalizer;
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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class BasketOverlapCalculator {

    private final BasketPriceResolver basketPriceResolver;

    public BasketOpportunity calculateOverlap(String etfIsin, EtfData etf,
            Map<String, EquityHoldings> userMap,
            Map<String, List<EquityHoldings>> userSectorMap,
            List<EquityHoldings> allUserHoldings,
            SectorProfile sectorProfile,
            Map<String, Double> prefetchedPrices) {

        List<BasketItem> composition = new ArrayList<>();
        List<BasketItem> buyList = new ArrayList<>();
        int matchCount = 0;
        int total = etf.getHoldings() != null ? etf.getHoldings().size() : 0;
        Map<String, Double> consumedWeightByIsin = new HashMap<>();

        Set<String> symbolsToFetch = new HashSet<>();
        if (etf.getHoldings() != null) {
            for (EtfHolding h : etf.getHoldings()) {
                if (h.getSymbol() != null && !h.getSymbol().isBlank()) {
                    symbolsToFetch.add(h.getSymbol());
                }
            }
        }
        for (EquityHoldings h : allUserHoldings) {
            if (h.getSymbol() != null && !h.getSymbol().isBlank()) {
                symbolsToFetch.add(h.getSymbol());
            }
        }

        Map<String, Double> prices;
        if (prefetchedPrices != null) {
            prices = new HashMap<>(prefetchedPrices);
            Set<String> gaps = new HashSet<>();
            for (String s : symbolsToFetch) {
                Double px = prices.get(s);
                if (px == null || px <= 0) {
                    gaps.add(s);
                }
            }
            if (!gaps.isEmpty()) {
                prices.putAll(basketPriceResolver.fetchPricesWithHoldingsFallback(gaps, allUserHoldings));
            }
        } else {
            prices = basketPriceResolver.fetchPricesWithHoldingsFallback(symbolsToFetch, allUserHoldings);
        }

        if (etf.getHoldings() != null) {
            class ItemReqPair {
                BasketItem item;
                EtfHolding req;

                ItemReqPair(BasketItem i, EtfHolding r) {
                    this.item = i;
                    this.req = r;
                }
            }
            List<ItemReqPair> pairs = new ArrayList<>();

            for (EtfHolding req : etf.getHoldings()) {
                BasketItem item = BasketItem.builder()
                        .stockSymbol(req.getSymbol())
                        .isin(req.getIsin())
                        .sector(req.getSector())
                        .etfWeight(req.getWeight())
                        .userWeight(0.0)
                        .replicaWeight(0.0)
                        .marketCapCategory(req.getMarketCapCategory())
                        .marketCapValue(req.getMarketCapValue())
                        .lastPrice(prices.get(req.getSymbol()))
                        .build();
                pairs.add(new ItemReqPair(item, req));
                composition.add(item);
            }

            for (ItemReqPair pair : pairs) {
                if (pair.req.getIsin() != null && userMap.containsKey(pair.req.getIsin())) {
                    boolean isMatch = processDirectMatch(pair.item, pair.req, userMap.get(pair.req.getIsin()),
                            consumedWeightByIsin, prices);
                    if (isMatch) {
                        matchCount++;
                    }
                }
            }

            for (ItemReqPair pair : pairs) {
                if (pair.item.getStatus() == ItemStatus.HELD) {
                    continue;
                }

                boolean handled = processSectorSubstitute(pair.item, pair.req, userSectorMap, consumedWeightByIsin,
                        allUserHoldings, prices, sectorProfile);
                if (handled) {
                    matchCount++;
                } else {
                    buyList.add(pair.item);
                }
            }

            refreshMissingAlternatives(composition, consumedWeightByIsin, allUserHoldings,
                    userSectorMap, prices, sectorProfile);
        }

        double maxPrice = 0.0;
        for (BasketItem item : composition) {
            if (item.getLastPrice() != null && item.getLastPrice() > maxPrice) {
                maxPrice = item.getLastPrice();
            }
        }
        double minimumInvestmentAmount = Math.max(maxPrice, 50000.0);

        double matchScore = (total == 0) ? 0 : (double) matchCount / total * 100.0;
        double heldScore = composition.stream().filter(i -> i.getStatus() == ItemStatus.HELD)
                .mapToDouble(i -> i.getEtfWeight() != null ? i.getEtfWeight() : 0.0).sum();
        double subScore = composition.stream().filter(i -> i.getStatus() == ItemStatus.SUBSTITUTE)
                .mapToDouble(i -> i.getEtfWeight() != null ? i.getEtfWeight() : 0.0).sum();

        double replicaScore = heldScore + subScore;

        return BasketOpportunity.builder()
                .etfIsin(etfIsin)
                .etfName(etf.getName())
                .matchScore(BasketUtils.round(matchScore))
                .replicaScore(BasketUtils.round(replicaScore))
                .readyToReplicate(replicaScore >= 90.0)
                .totalItems(total)
                .heldCount(matchCount)
                .missingCount(total - matchCount)
                .heldMatchScore(BasketUtils.round(heldScore))
                .substituteMatchScore(BasketUtils.round(subScore))
                .sectorialBasket(sectorProfile.sectorial)
                .dominantSector(sectorProfile.dominantSector)
                .etfConstituentIsins(sectorProfile.constituentIsins)
                .composition(composition)
                .buyList(buyList)
                .minimumInvestmentAmount(minimumInvestmentAmount)
                .build();
    }

    public SectorProfile detectSectorProfile(EtfData etf) {
        boolean isSectorial = false;
        String dominant = null;
        double maxWeight = 0;
        Map<String, Double> sectorWeights = new HashMap<>();
        List<String> constituentIsins = new ArrayList<>();
        if (etf.getHoldings() != null) {
            for (EtfHolding h : etf.getHoldings()) {
                if (h.getIsin() != null && !h.getIsin().isBlank()) {
                    constituentIsins.add(h.getIsin());
                }
                if (h.getSector() != null && h.getWeight() > 0) {
                    String sector = SectorNormalizer.normalizeFine(h.getSector());
                    sectorWeights.put(sector, sectorWeights.getOrDefault(sector, 0.0) + h.getWeight());
                }
            }
            for (Map.Entry<String, Double> entry : sectorWeights.entrySet()) {
                if (entry.getValue() > maxWeight) {
                    maxWeight = entry.getValue();
                    dominant = entry.getKey();
                }
                if (entry.getValue() > 75.0) {
                    isSectorial = true;
                }
            }
        }
        return new SectorProfile(isSectorial, dominant, constituentIsins);
    }

    public double getAvailableWeight(EquityHoldings h) {
        if (h == null) {
            return 0.0;
        }
        double physicalWeight = h.getWeightInPortfolio() != null ? h.getWeightInPortfolio() : 0.0;
        double physicalQty = h.getQuantity() != null ? h.getQuantity() : 0.0;
        double availableQty = h.getAvailableQuantity() != null ? h.getAvailableQuantity() : physicalQty;
        if (physicalQty > 0) {
            return (availableQty / physicalQty) * physicalWeight;
        }
        return 0.0;
    }

    public void refreshMissingAlternatives(
            List<BasketItem> composition,
            Map<String, Double> consumedWeightByIsin,
            List<EquityHoldings> allUserHoldings,
            Map<String, List<EquityHoldings>> userSectorMap,
            Map<String, Double> prices,
            SectorProfile sectorProfile) {
        for (BasketItem item : composition) {
            if (item.getStatus() != ItemStatus.MISSING) {
                continue;
            }
            PeerTiers tiers = collectPeerTiers(item.getSector(), item.getMarketCapCategory(), userSectorMap);
            double reqWeight = item.getEtfWeight() != null ? item.getEtfWeight() : 0.0;
            item.setAlternatives(buildTieredAlternatives(
                    item.getSector(), item.getMarketCapCategory(), reqWeight,
                    tiers.tier1, tiers.tier2, userSectorMap, allUserHoldings,
                    consumedWeightByIsin, prices, sectorProfile));
        }
    }

    private boolean processDirectMatch(BasketItem item, EtfHolding req, EquityHoldings userHolding,
            Map<String, Double> consumedWeightByIsin, Map<String, Double> prices) {
        log.info("Checking Held Item: {} | Qty: {} | AvgPrice: {}",
                userHolding.getSymbol(), userHolding.getQuantity(), userHolding.getAverageBuyingPrice());

        double consumed = consumedWeightByIsin.getOrDefault(userHolding.getIsin(), 0.0);
        double totalWeight = getAvailableWeight(userHolding);
        double available = totalWeight - consumed;

        if (available < 0.01) {
            return false;
        }

        item.setStatus(ItemStatus.HELD);
        item.setUserHoldingSymbol(userHolding.getSymbol());
        item.setUserHoldingIsin(userHolding.getIsin());
        item.setUserWeight(BasketUtils.round(totalWeight));
        item.setHeldQuantity(userHolding.getQuantity());
        item.setHeldAveragePrice(userHolding.getAverageBuyingPrice());

        Double price = prices.get(userHolding.getSymbol());
        if (price == null || price <= 0) {
            price = (userHolding.getCurrentPrice() != null && userHolding.getCurrentPrice() > 0)
                    ? userHolding.getCurrentPrice()
                    : userHolding.getAverageBuyingPrice();
        }
        if (price != null && price > 0) {
            item.setLastPrice(price);
        }

        double matchWeight = Math.min(req.getWeight(), available);
        item.setReplicaWeight(BasketUtils.round(matchWeight));

        consumedWeightByIsin.merge(userHolding.getIsin(), matchWeight, Double::sum);

        return true;
    }

    private boolean processSectorSubstitute(BasketItem item, EtfHolding req,
            Map<String, List<EquityHoldings>> userSectorMap,
            Map<String, Double> consumedWeightByIsin,
            List<EquityHoldings> allUserHoldings,
            Map<String, Double> prices,
            SectorProfile sectorProfile) {
        String sectorKey = SectorNormalizer.normalizeFine(req.getSector());
        boolean unknownSector = SectorNormalizer.isUnknown(req.getSector());

        List<EquityHoldings> tier1 = new ArrayList<>();
        List<EquityHoldings> tier2 = new ArrayList<>();

        if (sectorKey.startsWith("bank:")) {
            for (String key : userSectorMap.keySet()) {
                if (key.startsWith("bank:")) {
                    List<EquityHoldings> peers = userSectorMap.get(key);
                    for (EquityHoldings peer : peers) {
                        if (req.getMarketCapCategory() != null
                                && req.getMarketCapCategory().equalsIgnoreCase(peer.getMarketCapCategory())) {
                            tier1.add(peer);
                        } else {
                            tier2.add(peer);
                        }
                    }
                }
            }
        } else if (sectorKey.startsWith("financial:")) {
            for (String key : userSectorMap.keySet()) {
                if (key.startsWith("financial:") || key.startsWith("bank:")) {
                    List<EquityHoldings> peers = userSectorMap.get(key);
                    for (EquityHoldings peer : peers) {
                        if (req.getMarketCapCategory() != null
                                && req.getMarketCapCategory().equalsIgnoreCase(peer.getMarketCapCategory())) {
                            tier1.add(peer);
                        } else {
                            tier2.add(peer);
                        }
                    }
                }
            }
        } else if (!unknownSector) {
            List<EquityHoldings> sectorPeers = userSectorMap.getOrDefault(sectorKey, Collections.emptyList());
            for (EquityHoldings peer : sectorPeers) {
                if (req.getMarketCapCategory() != null
                        && req.getMarketCapCategory().equalsIgnoreCase(peer.getMarketCapCategory())) {
                    tier1.add(peer);
                } else {
                    tier2.add(peer);
                }
            }
        }

        if (!tier1.isEmpty() || !tier2.isEmpty()) {
            List<EquityHoldings> autoPeers = !tier1.isEmpty() ? tier1 : tier2;
            EquityHoldings substitute = autoPeers.stream()
                    .filter(p -> p.getIsin() != null
                            && (getAvailableWeight(p) - consumedWeightByIsin.getOrDefault(p.getIsin(), 0.0)) > 0.01)
                    .filter(p -> p.getAvailableQuantity() == null || p.getAvailableQuantity() > 0)
                    .max(Comparator.comparingDouble(
                            h -> getAvailableWeight(h) - consumedWeightByIsin.getOrDefault(h.getIsin(), 0.0)))
                    .orElse(null);

            if (substitute != null) {
                double consumed = consumedWeightByIsin.getOrDefault(substitute.getIsin(), 0.0);
                double totalWeight = getAvailableWeight(substitute);
                double available = totalWeight - consumed;

                if (available >= 0.01) {
                    double matchWeight = Math.min(req.getWeight(), available);

                    item.setStatus(ItemStatus.SUBSTITUTE);
                    item.setUserHoldingSymbol(substitute.getSymbol());
                    item.setUserHoldingIsin(substitute.getIsin());
                    item.setUserWeight(BasketUtils.round(totalWeight));

                    double physicalWeight = substitute.getWeightInPortfolio() != null ? substitute.getWeightInPortfolio()
                            : 0.0;
                    double physicalQty = substitute.getQuantity() != null ? substitute.getQuantity() : 0.0;
                    double allocatedQty = (physicalWeight > 0) ? (matchWeight / physicalWeight) * physicalQty
                            : (substitute.getAvailableQuantity() != null ? substitute.getAvailableQuantity() : 0.0);
                    item.setHeldQuantity(BasketUtils.round(allocatedQty));

                    item.setHeldAveragePrice(substitute.getAverageBuyingPrice());
                    item.setReason("Substitute: " + req.getSector()
                            + (req.getMarketCapCategory() != null ? "/" + req.getMarketCapCategory() : ""));

                    Double subPrice = prices.get(substitute.getSymbol());
                    if (subPrice == null || subPrice <= 0) {
                        subPrice = (substitute.getCurrentPrice() != null && substitute.getCurrentPrice() > 0)
                                ? substitute.getCurrentPrice()
                                : substitute.getAverageBuyingPrice();
                    }
                    if (subPrice != null && subPrice > 0) {
                        item.setLastPrice(subPrice);
                    }

                    item.setReplicaWeight(BasketUtils.round(matchWeight));

                    consumedWeightByIsin.merge(substitute.getIsin(), matchWeight, Double::sum);

                    return true;
                }
            }
        }

        List<BasketOpportunity.Alternative> alts = buildTieredAlternatives(
                req.getSector(), req.getMarketCapCategory(), req.getWeight(),
                tier1, tier2, userSectorMap, allUserHoldings, consumedWeightByIsin, prices, sectorProfile);
        item.setAlternatives(alts);

        item.setStatus(ItemStatus.MISSING);
        item.setUserWeight(0.0);
        item.setBuyQuantity(null);
        return false;
    }

    private List<BasketOpportunity.Alternative> buildTieredAlternatives(
            String sector,
            String marketCapCategory,
            double reqWeight,
            List<EquityHoldings> tier1,
            List<EquityHoldings> tier2,
            Map<String, List<EquityHoldings>> userSectorMap,
            List<EquityHoldings> allUserHoldings,
            Map<String, Double> consumedWeightByIsin,
            Map<String, Double> prices,
            SectorProfile sectorProfile) {
        List<BasketOpportunity.Alternative> alts = new ArrayList<>();

        alts.addAll(tier1.stream()
                .filter(p -> p.getIsin() != null
                        && (getAvailableWeight(p) - consumedWeightByIsin.getOrDefault(p.getIsin(), 0.0)) > 0.01)
                .filter(p -> p.getAvailableQuantity() == null || p.getAvailableQuantity() > 0)
                .sorted(Comparator.comparingDouble(
                        (EquityHoldings h) -> getAvailableWeight(h) - consumedWeightByIsin.getOrDefault(h.getIsin(), 0.0))
                        .reversed())
                .map(p -> toAlternative(p, reqWeight,
                        getAvailableWeight(p) - consumedWeightByIsin.getOrDefault(p.getIsin(), 0.0), prices, true))
                .collect(Collectors.toList()));

        alts.addAll(tier2.stream()
                .filter(p -> p.getIsin() != null
                        && (getAvailableWeight(p) - consumedWeightByIsin.getOrDefault(p.getIsin(), 0.0)) > 0.01)
                .filter(p -> p.getAvailableQuantity() == null || p.getAvailableQuantity() > 0)
                .sorted(Comparator.comparingDouble(
                        (EquityHoldings h) -> getAvailableWeight(h) - consumedWeightByIsin.getOrDefault(h.getIsin(), 0.0))
                        .reversed())
                .map(p -> toAlternative(p, reqWeight,
                        getAvailableWeight(p) - consumedWeightByIsin.getOrDefault(p.getIsin(), 0.0), prices, true))
                .collect(Collectors.toList()));

        appendTier3Alternatives(alts, sector, marketCapCategory, reqWeight, tier1, tier2,
                allUserHoldings, consumedWeightByIsin, prices, sectorProfile);

        if (alts.size() > 25) {
            return new ArrayList<>(alts.subList(0, 25));
        }
        return alts;
    }

    private void appendTier3Alternatives(
            List<BasketOpportunity.Alternative> alts,
            String sector,
            String marketCapCategory,
            double reqWeight,
            List<EquityHoldings> tier1,
            List<EquityHoldings> tier2,
            List<EquityHoldings> allUserHoldings,
            Map<String, Double> consumedWeightByIsin,
            Map<String, Double> prices,
            SectorProfile sectorProfile) {
        if (allUserHoldings == null || allUserHoldings.isEmpty()) {
            return;
        }
        Set<String> existingIsins = alts.stream()
                .map(BasketOpportunity.Alternative::getIsin)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> constituentSet = sectorProfile.constituentIsins != null
                ? new HashSet<>(sectorProfile.constituentIsins)
                : Collections.emptySet();

        List<EquityHoldings> tier3 = allUserHoldings.stream()
                .filter(p -> p.getIsin() != null && !existingIsins.contains(p.getIsin()))
                .filter(p -> (getAvailableWeight(p) - consumedWeightByIsin.getOrDefault(p.getIsin(), 0.0)) > 0.01)
                .filter(p -> p.getAvailableQuantity() == null || p.getAvailableQuantity() > 0)
                .filter(p -> !tier1.contains(p) && !tier2.contains(p))
                .sorted(Comparator
                        .comparingInt((EquityHoldings h) -> (h.getIsin() != null && constituentSet.contains(h.getIsin()))
                                ? 0 : 1)
                        .thenComparing(Comparator.comparingDouble(
                                (EquityHoldings h) -> getAvailableWeight(h)
                                        - consumedWeightByIsin.getOrDefault(h.getIsin(), 0.0)).reversed()))
                .limit(15)
                .collect(Collectors.toList());

        alts.addAll(tier3.stream()
                .map(p -> toAlternative(p, reqWeight,
                        getAvailableWeight(p) - consumedWeightByIsin.getOrDefault(p.getIsin(), 0.0), prices, false))
                .collect(Collectors.toList()));
    }

    private static class PeerTiers {
        final List<EquityHoldings> tier1;
        final List<EquityHoldings> tier2;

        PeerTiers(List<EquityHoldings> tier1, List<EquityHoldings> tier2) {
            this.tier1 = tier1;
            this.tier2 = tier2;
        }
    }

    private PeerTiers collectPeerTiers(String sector, String marketCapCategory,
            Map<String, List<EquityHoldings>> userSectorMap) {
        String sectorKey = SectorNormalizer.normalizeFine(sector);
        boolean unknownSector = SectorNormalizer.isUnknown(sector);
        List<EquityHoldings> tier1 = new ArrayList<>();
        List<EquityHoldings> tier2 = new ArrayList<>();

        if (sectorKey.startsWith("bank:")) {
            for (String key : userSectorMap.keySet()) {
                if (key.startsWith("bank:")) {
                    partitionByMarketCap(userSectorMap.get(key), marketCapCategory, tier1, tier2);
                }
            }
        } else if (sectorKey.startsWith("financial:")) {
            for (String key : userSectorMap.keySet()) {
                if (key.startsWith("financial:") || key.startsWith("bank:")) {
                    partitionByMarketCap(userSectorMap.get(key), marketCapCategory, tier1, tier2);
                }
            }
        } else if (!unknownSector) {
            List<EquityHoldings> sectorPeers = userSectorMap.getOrDefault(sectorKey, Collections.emptyList());
            partitionByMarketCap(sectorPeers, marketCapCategory, tier1, tier2);
        }
        return new PeerTiers(tier1, tier2);
    }

    private void partitionByMarketCap(List<EquityHoldings> peers, String marketCapCategory,
            List<EquityHoldings> tier1, List<EquityHoldings> tier2) {
        if (peers == null) {
            return;
        }
        for (EquityHoldings peer : peers) {
            if (marketCapCategory != null && marketCapCategory.equalsIgnoreCase(peer.getMarketCapCategory())) {
                tier1.add(peer);
            } else {
                tier2.add(peer);
            }
        }
    }

    private BasketOpportunity.Alternative toAlternative(EquityHoldings h, double reqWeight, double availableWeight,
            Map<String, Double> prices, boolean isSameSector) {
        boolean canFullyCover = availableWeight >= reqWeight;
        String coverageLabel = canFullyCover
                ? String.format("Covers %.1f%% gap fully", reqWeight)
                : String.format("Covers %.1f%% of %.1f%% gap", availableWeight, reqWeight);

        double physicalWeight = h.getWeightInPortfolio() != null ? h.getWeightInPortfolio() : 0.0;
        double physicalQty = h.getQuantity() != null ? h.getQuantity() : 0.0;
        double remainingQty = (physicalWeight > 0) ? (availableWeight / physicalWeight) * physicalQty
                : (h.getAvailableQuantity() != null ? h.getAvailableQuantity() : 0.0);

        return BasketOpportunity.Alternative.builder()
                .symbol(h.getSymbol())
                .isin(h.getIsin())
                .userWeight(BasketUtils.round(availableWeight))
                .quantity(BasketUtils.round(remainingQty))
                .lastPrice(prices != null && h.getSymbol() != null ? prices.get(h.getSymbol()) : null)
                .sector(h.getSector())
                .isSameSector(isSameSector)
                .canFullyCover(canFullyCover)
                .coverageLabel(coverageLabel)
                .build();
    }
}
