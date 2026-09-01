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
import com.portfolio.basket.exception.EtfNotFoundException;

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
            boolean includeHeld, List<String> excludedSymbols) {
        if (investmentAmount == null || investmentAmount <= 0) {
            return opportunity;
        }

        // 0. Mark excluded items and normalize remaining weights to sum to 100%
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

        // Holdings-only customize: allocate from existing portfolio — no buy orders.
        final double sizingAmount = investmentAmount;

        // 1. Gather symbols that still need a live/cached price (reuse lastPrice when present)
        Set<String> symbols = new HashSet<>();
        if (opportunity.getComposition() != null) {
            for (BasketItem item : opportunity.getComposition()) {
                boolean hasLast = item.getLastPrice() != null && item.getLastPrice() > 0;
                if (!hasLast && item.getStockSymbol() != null) {
                    symbols.add(item.getStockSymbol());
                }
                if (item.getUserHoldingSymbol() != null) {
                    // substitutes may need holding symbol even when ETF lastPrice exists
                    if (!hasLast || item.getStatus() == ItemStatus.SUBSTITUTE) {
                        symbols.add(item.getUserHoldingSymbol());
                    }
                }
            }
        }

        if (symbols.isEmpty() && (opportunity.getComposition() == null || opportunity.getComposition().isEmpty())) {
            return opportunity;
        }

        // 2. Fetch only missing prices; seed map from existing lastPrice
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

        // === PASS 1: Assign units from existing holdings (includeHeld) or buy list (legacy) ===
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
            if (price == null || price <= 0) price = item.getLastPrice();
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

        // Legacy buy-order path when not using held-only allocation
        if (!includeHeld && !gapAmounts.isEmpty()) {
            for (BasketItem item : opportunity.getComposition()) {
                if (!gapAmounts.containsKey(item.getStockSymbol())) continue;
                if (Boolean.TRUE.equals(item.getTargetQuantityLocked())) continue;
                Double price = item.getLastPrice();
                Double itemWeight = item.getRebalancedWeight() != null ? item.getRebalancedWeight() : item.getEtfWeight();
                double w = itemWeight != null ? itemWeight : 0.0;
                int buyQty = BasketUtils.resolveBuyQty(gapAmounts.get(item.getStockSymbol()), price, w);
                item.setBuyQuantity((double) buyQty);
            }
        }
        // Recalculate basket-level scores from updated composition (exclude EXCLUDED items from totals)
        if (opportunity.getComposition() != null) {
            int total = (int) opportunity.getComposition().stream()
                    .filter(i -> i.getStatus() != ItemStatus.EXCLUDED)
                    .count();
            int matchCount = 0;
            double replicaTotal = 0.0;
            for (BasketItem item : opportunity.getComposition()) {
                if (item.getStatus() == ItemStatus.EXCLUDED) continue;
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
                .mapToDouble(i -> i.getRebalancedWeight() != null ? i.getRebalancedWeight() : (i.getEtfWeight() != null ? i.getEtfWeight() : 0.0))
                .sum();
        opportunity.setHeldMatchScore(BasketUtils.round(heldScore));
        opportunity.setSubstituteMatchScore(BasketUtils.round(subScore));
        opportunity.setMissingMatchScore(BasketUtils.round(missingScore));

        // Compute and return actual investment cost and budget variance
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

        // Calculate Remaining Portfolio Value
        double remainingValue = userHoldings.stream()
                .mapToDouble(h -> {
                    double avail = h.getAvailableQuantity() != null ? h.getAvailableQuantity() : (h.getQuantity() != null ? h.getQuantity() : 0.0);
                    double price = h.getCurrentPrice() != null ? h.getCurrentPrice() : 0.0;
                    return avail * price;
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
        opportunities.forEach(op -> {
            op.setTotalPortfolioValue(totalValue);
            op.setRemainingPortfolioValue(remainingValue);
        });

        // 2. Sort by match score descending
        opportunities.sort(Comparator.comparingDouble(BasketOpportunity::getMatchScore).reversed());

        return opportunities;
    }

    private void resolveDiscoveryToken(String token, Set<String> out) {
        // Catalog alias first (e.g. "IT" / "Nifty IT" → ITBEES) — avoid remote ETF search
        Map<String, String> aliases = basketCatalogService.preferredSymbolByAlias();
        String aliasHit = aliases.get(token.toLowerCase(Locale.ROOT));
        if (aliasHit != null && !aliasHit.isBlank()) {
            out.add(aliasHit);
            return;
        }
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
                .collect(Collectors.groupingBy(h -> SectorNormalizer.normalizeFine(h.getSector())));

        List<BasketOpportunity> opportunities = new ArrayList<>();
        Map<String, EtfData> etfDataByInput = enrichedEtfService.getEnrichedEtfsBatch(new ArrayList<>(etfQueries));

        // One shared price fetch for all ETFs + user book (avoids N× getCurrentPrices)
        Set<String> symbolsToFetch = new HashSet<>();
        for (EquityHoldings h : userHoldings) {
            if (h.getSymbol() != null && !h.getSymbol().isBlank()) {
                symbolsToFetch.add(h.getSymbol());
            }
        }
        for (EtfData etf : etfDataByInput.values()) {
            if (etf == null || etf.getHoldings() == null) {
                continue;
            }
            for (EtfHolding holding : etf.getHoldings()) {
                if (holding.getSymbol() != null && !holding.getSymbol().isBlank()) {
                    symbolsToFetch.add(holding.getSymbol());
                }
            }
        }
        Map<String, Double> sharedPrices = fetchPricesWithHoldingsFallback(symbolsToFetch, userHoldings);
        log.info("Opportunities shared price map size={} for {} ETF queries", sharedPrices.size(), etfQueries.size());

        for (String etfQuery : etfQueries) {
            EtfData etf = etfDataByInput.get(etfQuery);
            if (etf == null) {
                log.warn("No ETF resolved for query '{}' after batch lookup", etfQuery);
                continue;
            }

            SectorProfile sectorProfile = detectSectorProfile(etf);

            BasketOpportunity opportunity = calculateOverlap(
                    etfQuery, etf, userMap, userSectorMap, userHoldings, sectorProfile, sharedPrices);
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
            throw new EtfNotFoundException(
                "ETF data not found for '" + etfIsin + "'. " +
                "Use the ETF symbol (e.g. BANKBEES) or verify the ETF exists in the catalog."
            );
        }

        // Convert into singleton list logic
        Map<String, EquityHoldings> userMap = userHoldings.stream()
                .collect(Collectors.toMap(EquityHoldings::getIsin, h -> h, (a, b) -> a));

        Map<String, List<EquityHoldings>> userSectorMap = userHoldings.stream()
                .filter(h -> h.getSector() != null && !SectorNormalizer.isUnknown(h.getSector()))
                .filter(h -> h.getAvailableQuantity() == null || h.getAvailableQuantity() > 0)
                .collect(Collectors.groupingBy(h -> SectorNormalizer.normalizeFine(h.getSector())));

        SectorProfile sectorProfile = detectSectorProfile(etf);

        BasketOpportunity opp = calculateOverlap(etfIsin, etf, userMap, userSectorMap, userHoldings, sectorProfile, null);

        // Calculate Total and Remaining Portfolio Value
        double totalValue = userHoldings.stream()
                .mapToDouble(h -> {
                    if (h.getCurrentValue() != null)
                        return h.getCurrentValue();
                    if (h.getInvestmentCost() != null)
                        return h.getInvestmentCost();
                    return 0.0;
                })
                .sum();
                
        double remainingValue = userHoldings.stream()
                .mapToDouble(h -> {
                    double avail = h.getAvailableQuantity() != null ? h.getAvailableQuantity() : (h.getQuantity() != null ? h.getQuantity() : 0.0);
                    double price = (h.getCurrentPrice() != null && h.getCurrentPrice() > 0) ? h.getCurrentPrice() : 
                                   (h.getAverageBuyingPrice() != null ? h.getAverageBuyingPrice() : 0.0);
                    return avail * price;
                })
                .sum();
                
        opp.setTotalPortfolioValue(totalValue);
        opp.setRemainingPortfolioValue(remainingValue);
        
        return opp;
    }

    private BasketOpportunity calculateOverlap(String etfIsin, EtfData etf,
            Map<String, EquityHoldings> userMap,
            Map<String, List<EquityHoldings>> userSectorMap,
            List<EquityHoldings> allUserHoldings,
            SectorProfile sectorProfile,
            Map<String, Double> prefetchedPrices) {

        List<BasketItem> composition = new ArrayList<>();
        List<BasketItem> buyList = new ArrayList<>();
        double replicaScoreTotal = 0;
        int matchCount = 0;
        int total = etf.getHoldings() != null ? etf.getHoldings().size() : 0;
        Map<String, Double> consumedWeightByIsin = new HashMap<>();

        // Gather all symbols from ETF holdings and User holdings
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
            // Fill any gaps still missing for this ETF from holdings / market
            Set<String> gaps = new HashSet<>();
            for (String s : symbolsToFetch) {
                Double px = prices.get(s);
                if (px == null || px <= 0) {
                    gaps.add(s);
                }
            }
            if (!gaps.isEmpty()) {
                prices.putAll(fetchPricesWithHoldingsFallback(gaps, allUserHoldings));
            }
        } else {
            prices = fetchPricesWithHoldingsFallback(symbolsToFetch, allUserHoldings);
        }

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
                    boolean isMatch = processDirectMatch(pair.item, pair.req, userMap.get(pair.req.getIsin()), consumedWeightByIsin, prices);
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
                
                boolean handled = processSectorSubstitute(pair.item, pair.req, userSectorMap, consumedWeightByIsin, allUserHoldings, prices, sectorProfile);
                if (handled) {
                    replicaScoreTotal += pair.item.getReplicaWeight();
                    matchCount++;
                } else {
                    buyList.add(pair.item);
                }
            }

            // Alternatives on early MISSING rows can go stale once later auto-subs consume the same peer.
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


    private double getAvailableWeight(EquityHoldings h) {
        if (h == null) return 0.0;
        double physicalWeight = h.getWeightInPortfolio() != null ? h.getWeightInPortfolio() : 0.0;
        double physicalQty = h.getQuantity() != null ? h.getQuantity() : 0.0;
        double availableQty = h.getAvailableQuantity() != null ? h.getAvailableQuantity() : physicalQty;
        if (physicalQty > 0) {
            return (availableQty / physicalQty) * physicalWeight;
        }
        return 0.0;
    }

    private boolean processDirectMatch(BasketItem item, EtfHolding req, EquityHoldings userHolding, Map<String, Double> consumedWeightByIsin, Map<String, Double> prices) {
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
        List<EquityHoldings> tier3 = new ArrayList<>();

        if (sectorKey.startsWith("bank:")) {
            for (String key : userSectorMap.keySet()) {
                if (key.startsWith("bank:")) {
                    List<EquityHoldings> peers = userSectorMap.get(key);
                    for (EquityHoldings peer : peers) {
                        if (req.getMarketCapCategory() != null && req.getMarketCapCategory().equalsIgnoreCase(peer.getMarketCapCategory())) {
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
                        if (req.getMarketCapCategory() != null && req.getMarketCapCategory().equalsIgnoreCase(peer.getMarketCapCategory())) {
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
                if (req.getMarketCapCategory() != null && req.getMarketCapCategory().equalsIgnoreCase(peer.getMarketCapCategory())) {
                    tier1.add(peer);
                } else {
                    tier2.add(peer);
                }
            }
        }

        // Auto-SUBSTITUTE stays same-sector only (never auto-pick cross-sector)
        if (!tier1.isEmpty() || !tier2.isEmpty()) {
            List<EquityHoldings> autoPeers = !tier1.isEmpty() ? tier1 : tier2;
            EquityHoldings substitute = autoPeers.stream()
                    .filter(p -> p.getIsin() != null && (getAvailableWeight(p) - consumedWeightByIsin.getOrDefault(p.getIsin(), 0.0)) > 0.01)
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
                    
                    double physicalWeight = substitute.getWeightInPortfolio() != null ? substitute.getWeightInPortfolio() : 0.0;
                    double physicalQty = substitute.getQuantity() != null ? substitute.getQuantity() : 0.0;
                    double allocatedQty = (physicalWeight > 0) ? (matchWeight / physicalWeight) * physicalQty : (substitute.getAvailableQuantity() != null ? substitute.getAvailableQuantity() : 0.0);
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
                .filter(p -> p.getIsin() != null && (getAvailableWeight(p) - consumedWeightByIsin.getOrDefault(p.getIsin(), 0.0)) > 0.01)
                .filter(p -> p.getAvailableQuantity() == null || p.getAvailableQuantity() > 0)
                .sorted(Comparator.comparingDouble((EquityHoldings h) -> getAvailableWeight(h) - consumedWeightByIsin.getOrDefault(h.getIsin(), 0.0)).reversed())
                .map(p -> toAlternative(p, reqWeight, getAvailableWeight(p) - consumedWeightByIsin.getOrDefault(p.getIsin(), 0.0), prices, true))
                .collect(Collectors.toList()));

        alts.addAll(tier2.stream()
                .filter(p -> p.getIsin() != null && (getAvailableWeight(p) - consumedWeightByIsin.getOrDefault(p.getIsin(), 0.0)) > 0.01)
                .filter(p -> p.getAvailableQuantity() == null || p.getAvailableQuantity() > 0)
                .sorted(Comparator.comparingDouble((EquityHoldings h) -> getAvailableWeight(h) - consumedWeightByIsin.getOrDefault(h.getIsin(), 0.0)).reversed())
                .map(p -> toAlternative(p, reqWeight, getAvailableWeight(p) - consumedWeightByIsin.getOrDefault(p.getIsin(), 0.0), prices, true))
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
                        .comparingInt((EquityHoldings h) -> (h.getIsin() != null && constituentSet.contains(h.getIsin())) ? 0 : 1)
                        .thenComparing(Comparator.comparingDouble(
                                (EquityHoldings h) -> getAvailableWeight(h) - consumedWeightByIsin.getOrDefault(h.getIsin(), 0.0)).reversed()))
                .limit(15)
                .collect(Collectors.toList());

        alts.addAll(tier3.stream()
                .map(p -> toAlternative(p, reqWeight,
                        getAvailableWeight(p) - consumedWeightByIsin.getOrDefault(p.getIsin(), 0.0), prices, false))
                .collect(Collectors.toList()));
    }

    private void refreshMissingAlternatives(
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
            String substituteKey = assignment.getSubstituteIsin();
            if (substituteKey == null || substituteKey.isBlank()) {
                continue;
            }
            BasketItem missingItem = missingByKey.get(missingKey);
            if (missingItem == null) {
                continue;
            }

            EquityHoldings sub = byIsin.get(substituteKey);
            if (sub == null) {
                sub = bySymbol.get(substituteKey.toUpperCase(Locale.ROOT));
            }
            if (sub == null) {
                continue;
            }

            String subIsin = sub.getIsin() != null ? sub.getIsin() : substituteKey;
            double neededWeight = assignment.getAssignedWeight() != null
                    ? assignment.getAssignedWeight()
                    : (missingItem.getEtfWeight() != null ? missingItem.getEtfWeight() : 0.0);
            double totalWeight = getAvailableWeight(sub);
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

    private BasketOpportunity.Alternative toAlternative(EquityHoldings h, double reqWeight, double availableWeight, Map<String, Double> prices, boolean isSameSector) {
        boolean canFullyCover = availableWeight >= reqWeight;
        String coverageLabel = canFullyCover 
                ? String.format("Covers %.1f%% gap fully", reqWeight) 
                : String.format("Covers %.1f%% of %.1f%% gap", availableWeight, reqWeight);
                
        double physicalWeight = h.getWeightInPortfolio() != null ? h.getWeightInPortfolio() : 0.0;
        double physicalQty = h.getQuantity() != null ? h.getQuantity() : 0.0;
        double remainingQty = (physicalWeight > 0) ? (availableWeight / physicalWeight) * physicalQty : (h.getAvailableQuantity() != null ? h.getAvailableQuantity() : 0.0);

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

    /**
     * Apply user-chosen substitute assignments on top of a fresh or existing preview.
     */
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
                consumedWeightByIsin.merge(item.getUserHoldingIsin(), item.getReplicaWeight() != null ? item.getReplicaWeight() : 0.0, Double::sum);
            }
        }

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

        Set<String> revertedAutoSubstituteSlots = new HashSet<>();
        planReclaimForExplicitAssignments(base, assignments, byIsin, bySymbol, consumedWeightByIsin, revertedAutoSubstituteSlots);

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
            String missingKey = originalItem.getIsin();
            if (missingKey == null) missingKey = originalItem.getStockSymbol();
            
            List<SubstituteAssignment> itemAssignments = assignmentsByMissing.get(originalItem.getIsin());
            if (itemAssignments == null) itemAssignments = assignmentsByMissing.get(originalItem.getStockSymbol());

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
                        (k, v) -> Math.max(0, v - (originalItem.getReplicaWeight() != null ? originalItem.getReplicaWeight() : 0.0)));
                }
            }
            
            for (SubstituteAssignment assignment : itemAssignments) {
                if (remainingEtfWeight <= 0.01) break;

                String substituteKey = assignment.getSubstituteIsin();
                if (substituteKey == null || substituteKey.isBlank()) continue;

                EquityHoldings sub = byIsin.get(substituteKey);
                if (sub == null) {
                    sub = bySymbol.get(substituteKey.toUpperCase(Locale.ROOT));
                }
                if (sub == null) {
                    warnings.add("Substitute not in holdings: " + substituteKey);
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

                String resolvedIsin = sub.getIsin() != null ? sub.getIsin() : substituteKey;
                double subWeight = getAvailableWeight(sub);
                
                double consumed = consumedWeightByIsin.getOrDefault(resolvedIsin, 0.0);
                double availableSubWeight = subWeight - consumed;

                if (availableSubWeight < 0.01) {
                    log.warn("Substitute ISIN fully consumed: {}", resolvedIsin);
                    warnings.add("Substitute fully consumed: " + sub.getSymbol());
                    continue;
                }

                double assignedWeight = assignment.getAssignedWeight() != null ? assignment.getAssignedWeight() : availableSubWeight;
                double matchWeight = Math.min(assignedWeight, availableSubWeight);
                matchWeight = Math.min(matchWeight, remainingEtfWeight);
                
                if (matchWeight < 0.01) continue;

                BasketItem splitItem = BasketItem.builder()
                        .stockSymbol(originalItem.getStockSymbol())
                        .isin(originalItem.getIsin())
                        .sector(originalItem.getSector())
                        .status(ItemStatus.SUBSTITUTE)
                        .userHoldingSymbol(sub.getSymbol())
                        .userHoldingIsin(sub.getIsin())
                        .reason("User swap: " + sub.getSymbol())
                        .etfWeight(matchWeight) // Split weight
                        .userWeight(BasketUtils.round(getAvailableWeight(sub)))
                        .marketCapCategory(originalItem.getMarketCapCategory())
                        .marketCapValue(originalItem.getMarketCapValue())
                        .targetQuantity(originalItem.getTargetQuantity())
                        .alternatives(originalItem.getAlternatives())
                        .build();

                consumedWeightByIsin.merge(resolvedIsin, matchWeight, Double::sum);

                double physicalWeight = sub.getWeightInPortfolio() != null ? sub.getWeightInPortfolio() : 0.0;
                double physicalQty = sub.getQuantity() != null ? sub.getQuantity() : 0.0;
                double allocatedQty = (physicalWeight > 0) ? (matchWeight / physicalWeight) * physicalQty : (sub.getAvailableQuantity() != null ? sub.getAvailableQuantity() : 0.0);
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
        refreshMissingAlternatives(newComposition, finalConsumed, userHoldings, userSectorMap, prices, sectorProfile);

        // Recalc scores
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
        EtfData etf = getEtfData(base.getEtfIsin());
        if (etf == null) {
            return;
        }
        SectorProfile profile = detectSectorProfile(etf);
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

    private static class SectorProfile {
        final boolean sectorial;
        final String dominantSector;
        final List<String> constituentIsins;

        SectorProfile(boolean sectorial, String dominantSector, List<String> constituentIsins) {
            this.sectorial = sectorial;
            this.dominantSector = dominantSector;
            this.constituentIsins = constituentIsins;
        }
    }

    private SectorProfile detectSectorProfile(EtfData etf) {
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

    /**
     * Market prices with holdings current/avg fallback — single batch when possible.
     */
    private Map<String, Double> fetchPricesWithHoldingsFallback(
            Set<String> symbolsToFetch, List<EquityHoldings> allUserHoldings) {
        Map<String, Double> prices = new HashMap<>();
        if (symbolsToFetch == null || symbolsToFetch.isEmpty()) {
            return prices;
        }
        try {
            Map<String, Double> fetched = marketDataService.getCurrentPrices(new ArrayList<>(symbolsToFetch));
            if (fetched != null) {
                prices.putAll(fetched);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch live prices for symbols: {}", e.getMessage());
        }
        if (allUserHoldings != null) {
            for (EquityHoldings h : allUserHoldings) {
                if (h.getSymbol() == null) {
                    continue;
                }
                Double existingPrice = prices.get(h.getSymbol());
                if (existingPrice == null || existingPrice <= 0) {
                    if (h.getCurrentPrice() != null && h.getCurrentPrice() > 0) {
                        prices.put(h.getSymbol(), h.getCurrentPrice());
                    } else if (h.getAverageBuyingPrice() != null && h.getAverageBuyingPrice() > 0) {
                        prices.put(h.getSymbol(), h.getAverageBuyingPrice());
                    }
                }
            }
        }
        return prices;
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

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class SubstituteAssignment {
        private String missingIsin;
        private String substituteIsin;
        private Double assignedWeight; // Null means consume up to max gap
    }
}
