package com.portfolio.basket.service;

import com.portfolio.basket.client.EtfApiClient;
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
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BasketEngineService {

    private final EtfApiClient etfApiClient;
    private final EnrichedEtfService enrichedEtfService;
    private final BasketCatalogService basketCatalogService;
    private final com.portfolio.marketdata.service.MarketDataService marketDataService;

    public BasketOpportunity calculateBasketQuantities(Double investmentAmount, BasketOpportunity opportunity,
            boolean includeHeld) {
        if (investmentAmount == null || investmentAmount <= 0) {
            return opportunity;
        }

        // 1. Gather all unique symbols from the composition (held + missing)
        Set<String> symbols = new HashSet<>();
        if (opportunity.getComposition() != null) {
            for (BasketItem item : opportunity.getComposition()) {
                if (item.getStockSymbol() != null) {
                    symbols.add(item.getStockSymbol());
                }
                if (item.getUserHoldingSymbol() != null) {
                    symbols.add(item.getUserHoldingSymbol());
                }
            }
        }

        if (symbols.isEmpty()) {
            return opportunity;
        }

        // 2. Fetch Live Prices
        log.info("Fetching live prices for {} symbols to calculate quantities", symbols.size());
        Map<String, Double> prices = marketDataService.getCurrentPrices(new ArrayList<>(symbols));

        // 3. Calculate Quantities
        for (BasketItem item : opportunity.getComposition()) {
            Double price = prices.get(item.getStockSymbol());
            if ((price == null || price <= 0) && item.getUserHoldingSymbol() != null) {
                price = prices.get(item.getUserHoldingSymbol());
            }
            if (price == null || price <= 0) {
                log.warn("Price not found for {} (or holding {}), defaulting quantity to 0", item.getStockSymbol(), item.getUserHoldingSymbol());
                item.setBuyQuantity(0.0);
                item.setLastPrice(null);
                continue;
            }

            // If includeHeld is false and item is HELD, we skip buying (quantity = 0)
            if (!includeHeld && item.getStatus() == ItemStatus.HELD) {
                item.setBuyQuantity(0.0);
                item.setLastPrice(price);
                continue;
            }

            // Target Amount for this stock based on ETF weight and total investment amount
            double targetAmount = (item.getEtfWeight() / 100.0) * investmentAmount;

            // If we are including held items, we must subtract the value of what we already
            // hold
            // so we only buy the "gap" (or nothing if over-held).
            if (includeHeld && (item.getStatus() == ItemStatus.HELD || item.getStatus() == ItemStatus.SUBSTITUTE)) {
                double existingValue = 0.0;

                // Method 1: Use Held Quantity * Current Price (Most Accurate)
                if (item.getHeldQuantity() != null && price != null) {
                    existingValue = item.getHeldQuantity() * price;
                }
                // Method 2: Fallback to User Weight (Less Accurate if TotalValue incorrect)
                else if (opportunity.getTotalPortfolioValue() != null && item.getUserWeight() != null) {
                    existingValue = (item.getUserWeight() / 100.0) * opportunity.getTotalPortfolioValue();
                } else {
                    log.warn("Cannot determine held value for {}. Qty: {}, TotalVal: {}",
                            item.getStockSymbol(), item.getHeldQuantity(), opportunity.getTotalPortfolioValue());
                }

                double requiredAmount = targetAmount - existingValue;

                if (requiredAmount <= 0) {
                    // We have enough or more than enough
                    item.setBuyQuantity(0.0);
                    item.setLastPrice(price);
                    continue;
                } else {
                    // We need to buy more to reach the target
                    targetAmount = requiredAmount;
                }
            }

            // Calculate quantity (floor)
            int quantity = (int) Math.floor(targetAmount / price);

            item.setBuyQuantity((double) quantity);
            item.setLastPrice(price);
        }
        // Recalculate basket-level scores from updated composition
        if (opportunity.getComposition() != null) {
            int total = opportunity.getComposition().size();
            int matchCount = 0;
            double replicaTotal = 0.0;
            for (BasketItem item : opportunity.getComposition()) {
                if (item.getStatus() == ItemStatus.HELD || item.getStatus() == ItemStatus.SUBSTITUTE) {
                    matchCount++;
                    replicaTotal += item.getReplicaWeight() != null ? item.getReplicaWeight() : 0.0;
                } else if (item.getBuyQuantity() != null && item.getBuyQuantity() > 0 
                           && item.getLastPrice() != null) {
                    // Recalculate replicaWeight based on actual purchased value
                    double purchasedValue = item.getBuyQuantity() * item.getLastPrice();
                    double replicaWeight = investmentAmount > 0 ? (purchasedValue / investmentAmount) * 100.0 : 0.0;
                    item.setReplicaWeight(BasketUtils.round(replicaWeight));
                    replicaTotal += item.getReplicaWeight();
                } else {
                    item.setReplicaWeight(0.0);
                }
            }
            opportunity.setMatchScore(BasketUtils.round(total == 0 ? 0 : (double) matchCount / total * 100.0));
            opportunity.setReplicaScore(BasketUtils.round(replicaTotal));
            opportunity.setReadyToReplicate(replicaTotal >= 70.0);
            opportunity.setHeldCount(matchCount);
            opportunity.setMissingCount(total - matchCount);
        }
        opportunity.setInvestmentAmount(investmentAmount);

        return opportunity;
    }

    public List<BasketOpportunity> findOpportunities(List<EquityHoldings> userHoldings, String etfQuery) {
        // 0. Calculate User Portfolio Weights
        BasketUtils.calculateUserWeights(userHoldings);

        // Calculate Total Portfolio Value using Current Value preference
        double totalValue = userHoldings.stream()
                .mapToDouble(h -> {
                    if (h.getCurrentValue() != null)
                        return h.getCurrentValue();
                    if (h.getInvestmentCost() != null)
                        return h.getInvestmentCost();
                    return 0.0;
                })
                .sum();

        // 1. Discover ETFs
        Set<String> allQueries = new LinkedHashSet<>();

        String effectiveQuery = (etfQuery == null || etfQuery.trim().isEmpty())
                ? basketCatalogService.resolveDefaultQuery()
                : etfQuery;
        if (effectiveQuery != null && !effectiveQuery.trim().isEmpty()) {
            if (effectiveQuery.contains(",")) {
                log.info("Processing query list: {}", effectiveQuery);
                for (String token : effectiveQuery.split(",")) {
                    String trimmed = token.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    resolveDiscoveryToken(trimmed, allQueries);
                }
            } else {
                log.info("Discovering ETFs via search query: {}", effectiveQuery);
                resolveDiscoveryToken(effectiveQuery.trim(), allQueries);
            }
        }

        if (allQueries.isEmpty()) {
            log.warn("No ETFs discovered for matching.");
            return Collections.emptyList();
        }

        log.info("Processing {} ETFs for matching", allQueries.size());
        List<BasketOpportunity> opportunities = findOpportunitiesInternal(userHoldings, allQueries);

        // Set total portfolio value on each opportunity
        opportunities.forEach(op -> op.setTotalPortfolioValue(totalValue));

        // 2. Sort by match score descending
        opportunities.sort(Comparator.comparingDouble(BasketOpportunity::getMatchScore).reversed());

        return opportunities;
    }

    private void resolveDiscoveryToken(String token, Set<String> out) {
        if (isLikelyIsinOrSymbol(token)) {
            out.add(token);
            return;
        }
        List<String> found = etfApiClient.searchEtfs(token);
        if (found.isEmpty()) {
            // Still try resolve via batch holdings (index name with spaces)
            out.add(token);
        } else {
            out.addAll(found);
        }
    }

    private boolean isLikelyIsinOrSymbol(String value) {
        if (value.contains(" ")) {
            return false;
        }
        if (value.matches("(?i)^INF[A-Z0-9]{10}$") || value.matches("(?i)^[A-Z]{2}[A-Z0-9]{10}$")) {
            return true;
        }
        return value.matches("^[A-Za-z0-9._-]+$") && value.length() <= 24;
    }

    // Helper to calculate opportunities for specific ETF queries (symbol / ISIN / name)
    private List<BasketOpportunity> findOpportunitiesInternal(List<EquityHoldings> userHoldings, Set<String> etfQueries) {
        Map<String, EquityHoldings> userMap = userHoldings.stream()
                .collect(Collectors.toMap(EquityHoldings::getIsin, h -> h, (a, b) -> a));

        Map<String, List<EquityHoldings>> userSectorMap = userHoldings.stream()
                .filter(h -> h.getSector() != null && !SectorNormalizer.isUnknown(h.getSector()))
                .filter(h -> h.getAvailableQuantity() == null || h.getAvailableQuantity() > 0)
                .collect(Collectors.groupingBy(h -> SectorNormalizer.normalize(h.getSector())));

        List<BasketOpportunity> opportunities = new ArrayList<>();
        Map<String, EtfData> etfDataByInput = enrichedEtfService.getEnrichedEtfsBatch(new ArrayList<>(etfQueries));

        for (String etfQuery : etfQueries) {
            EtfData etf = etfDataByInput.get(etfQuery);
            if (etf == null) {
                log.warn("No ETF resolved for query '{}' after batch lookup", etfQuery);
                continue;
            }

            BasketOpportunity opportunity = calculateOverlap(etfQuery, etf, userMap, userSectorMap, userHoldings);
            opportunities.add(opportunity);
        }

        return opportunities;
    }

    // Fetch Data from Live API Only (shared cache facade)
    public EtfData getEtfData(String isin) {
        log.info("Fetching enriched ETF data for ISIN/symbol: {}", isin);
        EtfData data = enrichedEtfService.getEnrichedEtf(isin);
        if (data == null) {
            log.warn("⚠️ No ETF data available from API for ISIN: {}", isin);
        }
        return data;
    }

    // Called by Controller for specific preview
    public BasketOpportunity getPreview(String etfIsin, List<EquityHoldings> userHoldings) {
        // 0. Calculate User Weights
        BasketUtils.calculateUserWeights(userHoldings);

        EtfData etf = getEtfData(etfIsin);
        if (etf == null) {
            throw new RuntimeException("ETF not found for ISIN: " + etfIsin);
        }

        // Convert into singleton list logic
        Map<String, EquityHoldings> userMap = userHoldings.stream()
                .collect(Collectors.toMap(EquityHoldings::getIsin, h -> h, (a, b) -> a));

        Map<String, List<EquityHoldings>> userSectorMap = userHoldings.stream()
                .filter(h -> h.getSector() != null && !SectorNormalizer.isUnknown(h.getSector()))
                .filter(h -> h.getAvailableQuantity() == null || h.getAvailableQuantity() > 0)
                .collect(Collectors.groupingBy(h -> SectorNormalizer.normalize(h.getSector())));

        return calculateOverlap(etfIsin, etf, userMap, userSectorMap, userHoldings);
    }

    private BasketOpportunity calculateOverlap(String etfIsin, EtfData etf,
            Map<String, EquityHoldings> userMap,
            Map<String, List<EquityHoldings>> userSectorMap,
            List<EquityHoldings> allUserHoldings) {

        List<BasketItem> composition = new ArrayList<>();
        List<BasketItem> buyList = new ArrayList<>();
        double replicaScoreTotal = 0;
        int matchCount = 0;
        int total = etf.getHoldings() != null ? etf.getHoldings().size() : 0;
        Map<String, Double> consumedWeightByIsin = new HashMap<>();

        // --- Fetch live prices for all constituents and user holdings in a single batch ---
        Set<String> symbolsForPrice = new HashSet<>();
        if (etf.getHoldings() != null) {
            for (EtfHolding req : etf.getHoldings()) {
                if (req.getSymbol() != null) symbolsForPrice.add(req.getSymbol());
            }
        }
        for (EquityHoldings h : allUserHoldings) {
            if (h.getSymbol() != null) symbolsForPrice.add(h.getSymbol());
        }
        Map<String, Double> prices = symbolsForPrice.isEmpty() ? Collections.emptyMap() 
                : marketDataService.getCurrentPrices(new ArrayList<>(symbolsForPrice));
        // -------------------------------------------------------------------------------

        if (etf.getHoldings() != null) {
            class ItemReqPair {
                BasketItem item;
                EtfHolding req;
                ItemReqPair(BasketItem i, EtfHolding r) { this.item = i; this.req = r; }
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

            // Pass 1: Direct Matches
            for (ItemReqPair pair : pairs) {
                if (pair.req.getIsin() != null && userMap.containsKey(pair.req.getIsin())) {
                    boolean isMatch = processDirectMatch(pair.item, pair.req, userMap.get(pair.req.getIsin()), consumedWeightByIsin);
                    if (isMatch) {
                        replicaScoreTotal += pair.item.getReplicaWeight();
                        matchCount++;
                    }
                }
            }

            // Pass 2: Sector Substitution
            for (ItemReqPair pair : pairs) {
                if (pair.item.getStatus() == ItemStatus.HELD) {
                    continue; // Already matched
                }
                
                boolean handled = processSectorSubstitute(pair.item, pair.req, userSectorMap, consumedWeightByIsin, allUserHoldings, prices);
                if (handled) {
                    replicaScoreTotal += pair.item.getReplicaWeight();
                    matchCount++;
                } else {
                    buyList.add(pair.item);
                }
            }
        }

        double matchScore = (total == 0) ? 0 : (double) matchCount / total * 100.0;
        double replicaScore = replicaScoreTotal;

        return BasketOpportunity.builder()
                .etfIsin(etfIsin)
                .etfName(etf.getName())
                .matchScore(BasketUtils.round(matchScore))
                .replicaScore(BasketUtils.round(replicaScore))
                .readyToReplicate(replicaScore >= 70.0)
                .totalItems(total)
                .heldCount(matchCount)
                .missingCount(total - matchCount)
                .composition(composition)
                .buyList(buyList)
                .build();
    }


    private double getAvailableWeight(EquityHoldings h) {
        if (h == null) return 0.0;
        double physicalWeight = h.getWeightInPortfolio() != null ? h.getWeightInPortfolio() : 0.0;
        double physicalQty = h.getQuantity() != null ? h.getQuantity() : 0.0;
        double availableQty = h.getAvailableQuantity() != null ? h.getAvailableQuantity() : 0.0;
        if (physicalQty > 0) {
            return (availableQty / physicalQty) * physicalWeight;
        }
        return 0.0;
    }

    private boolean processDirectMatch(BasketItem item, EtfHolding req, EquityHoldings userHolding, Map<String, Double> consumedWeightByIsin) {
        log.info("Checking Held Item: {} | Qty: {} | AvgPrice: {}",
                userHolding.getSymbol(), userHolding.getQuantity(), userHolding.getAverageBuyingPrice());

        double consumed = consumedWeightByIsin.getOrDefault(userHolding.getIsin(), 0.0);
        double totalWeight = getAvailableWeight(userHolding);
        double available = totalWeight - consumed;

        if (available < 0.01) {
            return false; // fully consumed
        }

        item.setStatus(ItemStatus.HELD);
        item.setUserHoldingSymbol(userHolding.getSymbol());
        item.setUserHoldingIsin(userHolding.getIsin());
        item.setUserWeight(BasketUtils.round(totalWeight));
        item.setHeldQuantity(userHolding.getAvailableQuantity());
        item.setHeldAveragePrice(userHolding.getAverageBuyingPrice());

        double matchWeight = Math.min(req.getWeight(), available);
        item.setReplicaWeight(BasketUtils.round(matchWeight));

        consumedWeightByIsin.merge(userHolding.getIsin(), matchWeight, Double::sum);

        return true;
    }

    private boolean processSectorSubstitute(BasketItem item, EtfHolding req,
            Map<String, List<EquityHoldings>> userSectorMap,
            Map<String, Double> consumedWeightByIsin,
            List<EquityHoldings> allUserHoldings,
            Map<String, Double> prices) {
        String sectorKey = SectorNormalizer.normalize(req.getSector());
        boolean unknownSector = SectorNormalizer.isUnknown(req.getSector());
        List<EquityHoldings> sectorPeers = unknownSector
                ? Collections.emptyList()
                : new ArrayList<>(userSectorMap.getOrDefault(sectorKey, Collections.emptyList()));

        if (req.getMarketCapCategory() != null && !sectorPeers.isEmpty()) {
            List<EquityHoldings> mcapPeers = sectorPeers.stream()
                    .filter(p -> req.getMarketCapCategory().equalsIgnoreCase(p.getMarketCapCategory()))
                    .collect(Collectors.toList());
            if (!mcapPeers.isEmpty()) {
                sectorPeers = mcapPeers;
            }
        }

        // Alternatives = unused peers (for UI swap)
        List<EquityHoldings> unusedPeers = sectorPeers.stream()
                .filter(p -> p.getIsin() != null && (getAvailableWeight(p) - consumedWeightByIsin.getOrDefault(p.getIsin(), 0.0)) > 0.01)
                .filter(p -> p.getAvailableQuantity() == null || p.getAvailableQuantity() > 0)
                .collect(Collectors.toList());

        List<BasketOpportunity.Alternative> alts = unusedPeers.stream()
                .map(p -> toAlternative(p, getAvailableWeight(p) - consumedWeightByIsin.getOrDefault(p.getIsin(), 0.0), prices))
                .collect(Collectors.toList());
        item.setAlternatives(alts);

        // Auto-SUBSTITUTE stays same-sector only (never auto-pick cross-sector / unknown)
        if (!unusedPeers.isEmpty()) {
            EquityHoldings substitute = unusedPeers.stream()
                    .max(Comparator.comparingDouble(
                            h -> getAvailableWeight(h) - consumedWeightByIsin.getOrDefault(h.getIsin(), 0.0)))
                    .orElse(unusedPeers.get(0));

            double consumed = consumedWeightByIsin.getOrDefault(substitute.getIsin(), 0.0);
            double totalWeight = getAvailableWeight(substitute);
            double available = totalWeight - consumed;

            if (available < 0.01) {
                // theoretically should not happen due to unusedPeers filter
                return false; 
            }

            item.setStatus(ItemStatus.SUBSTITUTE);
            item.setUserHoldingSymbol(substitute.getSymbol());
            item.setUserHoldingIsin(substitute.getIsin());
            item.setUserWeight(BasketUtils.round(totalWeight));
            item.setHeldQuantity(substitute.getAvailableQuantity());
            item.setHeldAveragePrice(substitute.getAverageBuyingPrice());
            item.setReason("Substitute: " + req.getSector()
                    + (req.getMarketCapCategory() != null ? "/" + req.getMarketCapCategory() : ""));

            double matchWeight = Math.min(req.getWeight(), available);
            item.setReplicaWeight(BasketUtils.round(matchWeight));

            consumedWeightByIsin.merge(substitute.getIsin(), matchWeight, Double::sum);

            return true;
        }

        // MISSING: fallback alternatives so UI Suggest swap stays actionable when unused holdings remain
        List<EquityHoldings> fallback = (allUserHoldings == null ? List.<EquityHoldings>of() : allUserHoldings)
                .stream()
                .filter(p -> p.getIsin() != null && (getAvailableWeight(p) - consumedWeightByIsin.getOrDefault(p.getIsin(), 0.0)) > 0.01)
                .filter(p -> p.getAvailableQuantity() == null || p.getAvailableQuantity() > 0)
                .sorted(Comparator
                        .comparing((EquityHoldings h) -> {
                            if (unknownSector || SectorNormalizer.isUnknown(h.getSector())) {
                                return 1;
                            }
                            return SectorNormalizer.normalize(h.getSector()).equals(sectorKey) ? 0 : 1;
                        })
                        .thenComparing(Comparator.comparingDouble(
                                (EquityHoldings h) -> getAvailableWeight(h)).reversed()))
                .collect(Collectors.toList());

        if (!fallback.isEmpty()) {
            item.setAlternatives(fallback.stream()
                    .map(p -> toAlternative(p, getAvailableWeight(p) - consumedWeightByIsin.getOrDefault(p.getIsin(), 0.0), prices))
                    .collect(Collectors.toList()));
        } else if (alts.isEmpty() && !sectorPeers.isEmpty()) {
            item.setAlternatives(sectorPeers.stream()
                    .filter(p -> p.getIsin() != null && (getAvailableWeight(p) - consumedWeightByIsin.getOrDefault(p.getIsin(), 0.0)) > 0.01)
                    .map(p -> toAlternative(p, getAvailableWeight(p) - consumedWeightByIsin.getOrDefault(p.getIsin(), 0.0), prices))
                    .collect(Collectors.toList()));
        }

        item.setStatus(ItemStatus.MISSING);
        item.setUserWeight(0.0);
        item.setBuyQuantity(1.0);
        return false;
    }

    private BasketOpportunity.Alternative toAlternative(EquityHoldings h, double availableWeight, Map<String, Double> prices) {
        return BasketOpportunity.Alternative.builder()
                .symbol(h.getSymbol())
                .isin(h.getIsin())
                .userWeight(BasketUtils.round(availableWeight))
                .quantity(h.getAvailableQuantity())
                .lastPrice(prices != null && h.getSymbol() != null ? prices.get(h.getSymbol()) : null)
                .build();
    }

    /**
     * Apply user-chosen substitute assignments on top of a fresh or existing preview.
     */
    public BasketOpportunity applySubstitutesOnExisting(BasketOpportunity base, List<EquityHoldings> userHoldings,
            List<SubstituteAssignment> assignments) {
        if (assignments == null || assignments.isEmpty() || base.getComposition() == null) {
            return base;
        }

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
                consumedWeightByIsin.merge(item.getUserHoldingIsin(), item.getReplicaWeight() != null ? item.getReplicaWeight() : 0.0, Double::sum);
            }
        }

        // --- Fetch live prices for substitutes being applied ---
        Set<String> subSymbols = new HashSet<>();
        for (SubstituteAssignment assignment : assignments) {
            if (assignment.getSubstituteIsin() != null) {
                EquityHoldings sub = byIsin.get(assignment.getSubstituteIsin());
                if (sub == null) sub = bySymbol.get(assignment.getSubstituteIsin().toUpperCase(Locale.ROOT));
                if (sub != null && sub.getSymbol() != null) subSymbols.add(sub.getSymbol());
            }
        }
        Map<String, Double> prices = subSymbols.isEmpty() ? Collections.emptyMap() 
                : marketDataService.getCurrentPrices(new ArrayList<>(subSymbols));
        // --------------------------------------------------------

        List<String> warnings = new ArrayList<>();
        for (SubstituteAssignment assignment : assignments) {
            if (assignment.getMissingIsin() == null || assignment.getSubstituteIsin() == null) {
                continue;
            }
            BasketItem item = base.getComposition().stream()
                    .filter(i -> assignment.getMissingIsin().equals(i.getIsin()))
                    .findFirst()
                    .orElse(null);
            if (item == null) {
                warnings.add("Unknown missing ISIN: " + assignment.getMissingIsin());
                continue;
            }
            if (item.getStatus() == ItemStatus.HELD) {
                warnings.add("Cannot substitute HELD row: " + assignment.getMissingIsin());
                continue;
            }
            EquityHoldings sub = byIsin.get(assignment.getSubstituteIsin());
            if (sub == null) {
                sub = bySymbol.get(assignment.getSubstituteIsin().toUpperCase(Locale.ROOT));
            }
            if (sub == null) {
                warnings.add("Substitute not in holdings: " + assignment.getSubstituteIsin());
                continue;
            }
            // Use ISIN from the resolved holding to ensure consistency
            String resolvedIsin = sub.getIsin() != null ? sub.getIsin() : assignment.getSubstituteIsin();

            double subWeight = getAvailableWeight(sub);

            // Free previous auto-sub if flipping from SUBSTITUTE
            if (item.getStatus() == ItemStatus.SUBSTITUTE && item.getUserHoldingIsin() != null) {
                consumedWeightByIsin.computeIfPresent(item.getUserHoldingIsin(), 
                    (k, v) -> v - (item.getReplicaWeight() != null ? item.getReplicaWeight() : 0.0));
            }

            double consumed = consumedWeightByIsin.getOrDefault(resolvedIsin, 0.0);
            if ((subWeight - consumed) < 0.01) {
                throw new IllegalStateException(
                        "Substitute ISIN fully consumed: " + resolvedIsin);
            }
            
            double matchWeight = Math.min(
                    item.getEtfWeight() != null ? item.getEtfWeight() : 0.0,
                    subWeight - consumed);
                    
            consumedWeightByIsin.merge(resolvedIsin, matchWeight, Double::sum);

            item.setStatus(ItemStatus.SUBSTITUTE);
            item.setUserHoldingSymbol(sub.getSymbol());
            item.setUserHoldingIsin(sub.getIsin());
            item.setUserWeight(BasketUtils.round(getAvailableWeight(sub)));
            item.setHeldQuantity(sub.getAvailableQuantity());
            item.setHeldAveragePrice(sub.getAverageBuyingPrice());
            item.setReason("User swap: " + sub.getSymbol());
            item.setReplicaWeight(BasketUtils.round(matchWeight));
            
            Double subPrice = prices.get(sub.getSymbol());
            if (subPrice != null) {
                item.setLastPrice(subPrice);
            }
        }

        // Recalc scores
        int total = base.getComposition().size();
        int matchCount = 0;
        double replica = 0;
        List<BasketItem> buyList = new ArrayList<>();
        for (BasketItem item : base.getComposition()) {
            if (item.getStatus() == ItemStatus.HELD || item.getStatus() == ItemStatus.SUBSTITUTE) {
                matchCount++;
                replica += item.getReplicaWeight() != null ? item.getReplicaWeight() : 0;
            } else {
                buyList.add(item);
            }
        }
        base.setHeldCount(matchCount);
        base.setMissingCount(total - matchCount);
        base.setMatchScore(BasketUtils.round(total == 0 ? 0 : (double) matchCount / total * 100.0));
        base.setReplicaScore(BasketUtils.round(replica));
        base.setReadyToReplicate(replica >= 70.0);
        base.setBuyList(buyList);
        return base;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class SubstituteAssignment {
        private String missingIsin;
        private String substituteIsin;
    }
}
