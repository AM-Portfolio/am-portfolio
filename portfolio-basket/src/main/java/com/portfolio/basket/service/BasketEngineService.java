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

            if (totalActiveWeight > 0 && totalActiveWeight < 99.99) {
                double multiplier = 100.0 / totalActiveWeight;
                for (BasketItem item : activeItems) {
                    item.setRebalancedWeight(BasketUtils.round(
                            (item.getEtfWeight() != null ? item.getEtfWeight() : 0.0) * multiplier));
                }
            }
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

        // === PASS 1: Calculate base target quantities and initial buy requirements ===
        for (BasketItem item : opportunity.getComposition()) {
            if (excluded.contains(item.getStockSymbol())) {
                item.setBuyQuantity(0.0);
                item.setTargetQuantity(0.0);
                item.setUnderfunded(false);
                continue;
            }

            Double price = prices.get(item.getStockSymbol());
            if ((price == null || price <= 0) && item.getUserHoldingSymbol() != null) {
                price = prices.get(item.getUserHoldingSymbol());
            }
            if (price == null || price <= 0) {
                price = item.getLastPrice(); // Fallback to DB
            }
            if (price == null || price <= 0) {
                log.warn("Price totally missing for {}, defaulting quantity to 0", item.getStockSymbol());
                item.setBuyQuantity(0.0);
                item.setTargetQuantity(0.0);
                item.setUnderfunded(false);
                continue;
            }
            item.setLastPrice(price);

            double weight = item.getRebalancedWeight() != null ? item.getRebalancedWeight() : (item.getEtfWeight() != null ? item.getEtfWeight() : 0.0);
            double targetAmount = (weight / 100.0) * investmentAmount;
            int targetQty = (int) Math.floor(targetAmount / price);
            item.setTargetQuantity((double) targetQty);

            double heldQty = item.getHeldQuantity() != null ? item.getHeldQuantity() : 0.0;
            if (includeHeld && (item.getStatus() == ItemStatus.HELD || item.getStatus() == ItemStatus.SUBSTITUTE)) {
                int buyQty = Math.max(0, targetQty - (int) heldQty);
                item.setBuyQuantity((double) buyQty);
            } else if (item.getStatus() == ItemStatus.MISSING) {
                item.setBuyQuantity((double) targetQty);
            } else {
                item.setBuyQuantity(0.0);
            }

            // Mark underfunded if stock is missing and unit price exceeds target allocation budget
            boolean isUnderfunded = item.getStatus() == ItemStatus.MISSING && targetQty == 0 && price > targetAmount;
            item.setUnderfunded(isUnderfunded);
        }

        // === PASS 2: Greedy residual cash redistribution ===
        double currentCashSpent = opportunity.getComposition().stream()
                .filter(i -> !excluded.contains(i.getStockSymbol()))
                .filter(i -> i.getBuyQuantity() != null && i.getBuyQuantity() > 0 && i.getLastPrice() != null)
                .mapToDouble(i -> i.getBuyQuantity() * i.getLastPrice())
                .sum();
        double remainingCash = investmentAmount - currentCashSpent;

        if (remainingCash > 0) {
            final double maxResidualBudget = remainingCash;
            List<BasketItem> candidates = opportunity.getComposition().stream()
                    .filter(i -> !excluded.contains(i.getStockSymbol()))
                    .filter(i -> i.getLastPrice() != null && i.getLastPrice() > 0 && i.getLastPrice() <= maxResidualBudget)
                    .sorted((a, b) -> {
                        double wa = a.getRebalancedWeight() != null ? a.getRebalancedWeight() : (a.getEtfWeight() != null ? a.getEtfWeight() : 0.0);
                        double wb = b.getRebalancedWeight() != null ? b.getRebalancedWeight() : (b.getEtfWeight() != null ? b.getEtfWeight() : 0.0);
                        return Double.compare(wb, wa);
                    })
                    .collect(Collectors.toList());

            for (BasketItem item : candidates) {
                double p = item.getLastPrice();
                if (remainingCash >= p) {
                    double currentBuy = item.getBuyQuantity() != null ? item.getBuyQuantity() : 0.0;
                    item.setBuyQuantity(currentBuy + 1.0);
                    if (item.getTargetQuantity() != null) {
                        item.setTargetQuantity(item.getTargetQuantity() + 1.0);
                    }
                    remainingCash -= p;
                }
            }
        }
        opportunity.setResidualCash(BasketUtils.round(remainingCash));

        // === PASS 3: Recalculate basket-level scores and metadata ===
        if (opportunity.getComposition() != null) {
            int total = (int) opportunity.getComposition().stream()
                    .filter(i -> i.getStatus() != ItemStatus.EXCLUDED)
                    .count();
            int matchCount = 0;
            double totalCoveredWeight = 0.0;

            for (BasketItem item : opportunity.getComposition()) {
                if (item.getStatus() == ItemStatus.EXCLUDED) continue;
                double w = item.getRebalancedWeight() != null ? item.getRebalancedWeight() : (item.getEtfWeight() != null ? item.getEtfWeight() : 0.0);
                double heldQty = item.getHeldQuantity() != null ? item.getHeldQuantity() : 0.0;
                double buyQty = item.getBuyQuantity() != null ? item.getBuyQuantity() : 0.0;

                if (heldQty > 0 || buyQty > 0) {
                    matchCount++;
                    item.setReplicaWeight(BasketUtils.round(w));
                    totalCoveredWeight += w;
                } else {
                    item.setReplicaWeight(0.0);
                }
            }

            opportunity.setMatchScore(BasketUtils.round(total == 0 ? 0 : (double) matchCount / total * 100.0));
            opportunity.setReplicaScore(Math.min(100.0, BasketUtils.round(totalCoveredWeight)));
            opportunity.setReadyToReplicate(opportunity.getReplicaScore() >= 90.0);
            opportunity.setHeldCount(matchCount);
            opportunity.setMissingCount(total - matchCount);
        }

        opportunity.setInvestmentAmount(investmentAmount);

        double heldScore = opportunity.getComposition().stream()
                .filter(i -> i.getStatus() == ItemStatus.HELD)
                .mapToDouble(i -> i.getReplicaWeight() != null ? i.getReplicaWeight() : 0.0).sum();
        double subScore = opportunity.getComposition().stream()
                .filter(i -> i.getStatus() == ItemStatus.SUBSTITUTE)
                .mapToDouble(i -> i.getReplicaWeight() != null ? i.getReplicaWeight() : 0.0).sum();
        opportunity.setHeldMatchScore(BasketUtils.round(heldScore));
        opportunity.setSubstituteMatchScore(BasketUtils.round(subScore));

        // Compute and return actual investment cost, variance, residual cash, and budget utilization
        double actualCost = opportunity.getComposition().stream()
                .filter(i -> i.getBuyQuantity() != null && i.getBuyQuantity() > 0 && i.getLastPrice() != null)
                .mapToDouble(i -> i.getBuyQuantity() * i.getLastPrice())
                .sum();
        opportunity.setActualInvestmentCost(BasketUtils.round(actualCost));
        opportunity.setBudgetVariance(BasketUtils.round(actualCost - investmentAmount));
        opportunity.setResidualCash(BasketUtils.round(Math.max(0, investmentAmount - actualCost)));
        opportunity.setBudgetUtilization(investmentAmount > 0 ? BasketUtils.round((actualCost / investmentAmount) * 100.0) : 0.0);
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

        BasketOpportunity opp = calculateOverlap(etfIsin, etf, userMap, userSectorMap, userHoldings);

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
            List<EquityHoldings> allUserHoldings) {

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

        Map<String, Double> prices = new HashMap<>();
        try {
            if (!symbolsToFetch.isEmpty()) {
                prices = marketDataService.getCurrentPrices(new ArrayList<>(symbolsToFetch));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch live prices for preview symbols: {}", e.getMessage());
        }
        if (prices == null) {
            prices = new HashMap<>();
        }
        // Add fallback to user's average buying price or current price
        for (EquityHoldings h : allUserHoldings) {
            if (h.getSymbol() != null) {
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
                
                boolean handled = processSectorSubstitute(pair.item, pair.req, userSectorMap, consumedWeightByIsin, allUserHoldings, prices);
                if (handled) {
                    replicaScoreTotal += pair.item.getReplicaWeight();
                    matchCount++;
                } else {
                    buyList.add(pair.item);
                }
            }
        }

        double maxPrice = 0.0;
        for (BasketItem item : composition) {
            if (item.getLastPrice() != null && item.getLastPrice() > maxPrice) {
                maxPrice = item.getLastPrice();
            }
        }
        double minimumInvestmentAmount = Math.max(maxPrice, 50000.0);

        double matchScore = (total == 0) ? 0 : (double) matchCount / total * 100.0;
        double replicaScore = replicaScoreTotal;

        double heldScore = composition.stream().filter(i -> i.getStatus() == ItemStatus.HELD)
                .mapToDouble(i -> i.getReplicaWeight() != null ? i.getReplicaWeight() : 0.0).sum();
        double subScore = composition.stream().filter(i -> i.getStatus() == ItemStatus.SUBSTITUTE)
                .mapToDouble(i -> i.getReplicaWeight() != null ? i.getReplicaWeight() : 0.0).sum();

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
                .composition(composition)
                .buyList(buyList)
                .minimumInvestmentAmount(minimumInvestmentAmount)
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
            Map<String, Double> prices) {
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
                    item.setStatus(ItemStatus.SUBSTITUTE);
                    item.setUserHoldingSymbol(substitute.getSymbol());
                    item.setUserHoldingIsin(substitute.getIsin());
                    item.setUserWeight(BasketUtils.round(totalWeight));
                    item.setHeldQuantity(substitute.getAvailableQuantity());
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

                    double matchWeight = Math.min(req.getWeight(), available);
                    item.setReplicaWeight(BasketUtils.round(matchWeight));

                    consumedWeightByIsin.merge(substitute.getIsin(), matchWeight, Double::sum);

                    return true;
                }
            }
        }

        // MISSING: Tiered alternatives for UI
        List<BasketOpportunity.Alternative> alts = new ArrayList<>();
        
        // Add Tier 1 (Same Sector + Same Market Cap)
        alts.addAll(tier1.stream()
                .filter(p -> p.getIsin() != null && (getAvailableWeight(p) - consumedWeightByIsin.getOrDefault(p.getIsin(), 0.0)) > 0.01)
                .filter(p -> p.getAvailableQuantity() == null || p.getAvailableQuantity() > 0)
                .sorted(Comparator.comparingDouble((EquityHoldings h) -> getAvailableWeight(h) - consumedWeightByIsin.getOrDefault(h.getIsin(), 0.0)).reversed())
                .map(p -> toAlternative(p, req.getWeight(), getAvailableWeight(p) - consumedWeightByIsin.getOrDefault(p.getIsin(), 0.0), prices, true))
                .collect(Collectors.toList()));
                
        // Add Tier 2 (Same Sector)
        alts.addAll(tier2.stream()
                .filter(p -> p.getIsin() != null && (getAvailableWeight(p) - consumedWeightByIsin.getOrDefault(p.getIsin(), 0.0)) > 0.01)
                .filter(p -> p.getAvailableQuantity() == null || p.getAvailableQuantity() > 0)
                .sorted(Comparator.comparingDouble((EquityHoldings h) -> getAvailableWeight(h) - consumedWeightByIsin.getOrDefault(h.getIsin(), 0.0)).reversed())
                .map(p -> toAlternative(p, req.getWeight(), getAvailableWeight(p) - consumedWeightByIsin.getOrDefault(p.getIsin(), 0.0), prices, true))
                .collect(Collectors.toList()));
                
        // Add Tier 3 (Cross Sector) only if Tiers 1 and 2 are empty or very sparse
        if (alts.isEmpty() && allUserHoldings != null) {
            tier3 = allUserHoldings.stream()
                .filter(p -> p.getIsin() != null && (getAvailableWeight(p) - consumedWeightByIsin.getOrDefault(p.getIsin(), 0.0)) > 0.01)
                .filter(p -> p.getAvailableQuantity() == null || p.getAvailableQuantity() > 0)
                .filter(p -> !tier1.contains(p) && !tier2.contains(p))
                .sorted(Comparator.comparingDouble((EquityHoldings h) -> getAvailableWeight(h) - consumedWeightByIsin.getOrDefault(h.getIsin(), 0.0)).reversed())
                .collect(Collectors.toList());
                
            alts.addAll(tier3.stream()
                .map(p -> toAlternative(p, req.getWeight(), getAvailableWeight(p) - consumedWeightByIsin.getOrDefault(p.getIsin(), 0.0), prices, false))
                .collect(Collectors.toList()));
        }

        // Limit to top 10 alternatives total
        if (alts.size() > 10) {
            alts = alts.subList(0, 10);
        }
        item.setAlternatives(alts);

        item.setStatus(ItemStatus.MISSING);
        item.setUserWeight(0.0);
        item.setBuyQuantity(null);
        return false;
    }

    private BasketOpportunity.Alternative toAlternative(EquityHoldings h, double reqWeight, double availableWeight, Map<String, Double> prices, boolean isSameSector) {
        boolean canFullyCover = availableWeight >= reqWeight;
        String coverageLabel = canFullyCover 
                ? String.format("Matching %.1f%% gap fully", reqWeight) 
                : String.format("Matching %.1f%% of %.1f%% gap", availableWeight, reqWeight);
                
        return BasketOpportunity.Alternative.builder()
                .symbol(h.getSymbol())
                .isin(h.getIsin())
                .userWeight(BasketUtils.round(availableWeight))
                .quantity(h.getAvailableQuantity())
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
            String missingKey = assignment.getMissingIsin();
            String substituteKey = assignment.getSubstituteIsin();

            if (missingKey == null || missingKey.isBlank() || substituteKey == null || substituteKey.isBlank()) {
                continue;
            }
            BasketItem item = base.getComposition().stream()
                    .filter(i -> missingKey.equalsIgnoreCase(i.getIsin()) || missingKey.equalsIgnoreCase(i.getStockSymbol()))
                    .findFirst()
                    .orElse(null);
            if (item == null) {
                warnings.add("Unknown missing item (tried ISIN+Symbol): " + missingKey);
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
                log.warn("Substitute ISIN fully consumed: {}. Assigning 0% coverage to {}", resolvedIsin, item.getStockSymbol());
                item.setStatus(ItemStatus.SUBSTITUTE);
                item.setUserHoldingSymbol(sub.getSymbol());
                item.setUserHoldingIsin(sub.getIsin());
                item.setReplicaWeight(0.0);
                item.setReason("User swap (weight exhausted): " + sub.getSymbol());
                continue;
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
            if (subPrice == null || subPrice <= 0) {
                subPrice = (sub.getCurrentPrice() != null && sub.getCurrentPrice() > 0)
                        ? sub.getCurrentPrice()
                        : sub.getAverageBuyingPrice();
            }
            if (subPrice != null && subPrice > 0) {
                item.setLastPrice(subPrice);
            }
        }

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
        base.setHeldCount(matchCount);
        base.setMissingCount(total - matchCount);
        base.setMatchScore(BasketUtils.round(total == 0 ? 0 : (double) matchCount / total * 100.0));
        base.setHeldMatchScore(BasketUtils.round(total == 0 ? 0 : (double) heldCount / total * 100.0));
        base.setSubstituteMatchScore(BasketUtils.round(total == 0 ? 0 : (double) subCount / total * 100.0));
        base.setReplicaScore(BasketUtils.round(replica));
        base.setReadyToReplicate(replica >= 90.0);
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
