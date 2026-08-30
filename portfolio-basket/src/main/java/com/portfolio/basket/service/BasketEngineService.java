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

        // 0b. Compute effective investment amount adjusting for held items
        //     (This logic previously lived in the Flutter UI — moved here for correct architecture)
        double effectiveInvestmentAmount = investmentAmount;
        if (includeHeld && opportunity.getComposition() != null) {
            double totalHeldCurrentValue = opportunity.getComposition().stream()
                    .filter(i -> !excluded.contains(i.getStockSymbol()))
                    .filter(i -> i.getStatus() == ItemStatus.HELD || i.getStatus() == ItemStatus.SUBSTITUTE)
                    .mapToDouble(i -> {
                        double qty = i.getHeldQuantity() != null ? i.getHeldQuantity() : 0;
                        double price = i.getLastPrice() != null ? i.getLastPrice() : 0;
                        return qty * price;
                    }).sum();
            double totalHeldCost = opportunity.getComposition().stream()
                    .filter(i -> !excluded.contains(i.getStockSymbol()))
                    .filter(i -> i.getStatus() == ItemStatus.HELD || i.getStatus() == ItemStatus.SUBSTITUTE)
                    .mapToDouble(i -> {
                        double qty = i.getHeldQuantity() != null ? i.getHeldQuantity() : 0;
                        double avgPrice = i.getHeldAveragePrice() != null ? i.getHeldAveragePrice() : 0;
                        return qty * avgPrice;
                    }).sum();
            if (investmentAmount > totalHeldCost) {
                effectiveInvestmentAmount = totalHeldCurrentValue + (investmentAmount - totalHeldCost);
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

        // === PASS 1: Calculate base targets and collect surplus ===
        Map<String, Double> surplusPoolMap = new HashMap<>();
        Map<String, Double> gapAmounts = new HashMap<>();
        double totalSurplus = 0.0;

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

            double weight = item.getRebalancedWeight() != null ? item.getRebalancedWeight() : (item.getEtfWeight() != null ? item.getEtfWeight() : 0.0);
            double baseTargetAmount = (weight / 100.0) * effectiveInvestmentAmount;
            int baseTargetQty = (int) Math.floor(baseTargetAmount / price);

            if (!includeHeld && item.getStatus() == ItemStatus.HELD) {
                item.setBuyQuantity(0.0);
                item.setTargetQuantity((double) baseTargetQty);
                continue;
            }

            if (includeHeld && (item.getStatus() == ItemStatus.HELD || item.getStatus() == ItemStatus.SUBSTITUTE)) {
                double heldValue = (item.getHeldQuantity() != null ? item.getHeldQuantity() : 0) * price;
                if (heldValue >= baseTargetAmount) {
                    // Over-held -> no purchases needed
                    item.setBuyQuantity(0.0);
                    item.setTargetQuantity((double) baseTargetQty);
                } else {
                    // Under-held -> needs purchases
                    double gap = baseTargetAmount - heldValue;
                    gapAmounts.put(item.getStockSymbol(), gap);
                    item.setTargetQuantity((double) baseTargetQty);
                }
            } else if (item.getStatus() == ItemStatus.MISSING) {
                // Missing -> needs full purchases
                gapAmounts.put(item.getStockSymbol(), baseTargetAmount);
                item.setTargetQuantity((double) baseTargetQty);
            }
        }

        // === PASS 2: Redistribute surplus proportionally to needy items ===
        if (totalSurplus > 0 && !gapAmounts.isEmpty()) {
            double totalGapWeight = gapAmounts.keySet().stream()
                    .mapToDouble(sym -> {
                        BasketItem it = opportunity.getComposition().stream().filter(i -> i.getStockSymbol().equals(sym)).findFirst().orElse(null);
                        if (it == null) return 0.0;
                        Double w = it.getRebalancedWeight() != null ? it.getRebalancedWeight() : it.getEtfWeight();
                        return w != null ? w : 0.0;
                    }).sum();

            if (totalGapWeight > 0) {
                for (BasketItem item : opportunity.getComposition()) {
                    if (!gapAmounts.containsKey(item.getStockSymbol())) continue;
                    Double itemWeight = item.getRebalancedWeight() != null ? item.getRebalancedWeight() : item.getEtfWeight();
                    double bonus = totalSurplus * ((itemWeight != null ? itemWeight : 0.0) / totalGapWeight);
                    double finalGapAmount = gapAmounts.get(item.getStockSymbol()) + bonus;
                    Double price = item.getLastPrice();
                    int buyQty = (int) Math.floor(finalGapAmount / price);
                    item.setBuyQuantity((double) buyQty);
                    
                    // Update targetQuantity to reflect surplus-adjusted target
                    double heldQty = item.getHeldQuantity() != null ? item.getHeldQuantity() : 0.0;
                    item.setTargetQuantity(heldQty + buyQty);
                }
            }
        } else {
            // No surplus or no gaps -> just set buy quantity based on base target amount gap
            for (BasketItem item : opportunity.getComposition()) {
                if (gapAmounts.containsKey(item.getStockSymbol())) {
                    Double price = item.getLastPrice();
                    int buyQty = (int) Math.floor(gapAmounts.get(item.getStockSymbol()) / price);
                    item.setBuyQuantity((double) buyQty);
                }
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
                if (item.getStatus() == ItemStatus.HELD || item.getStatus() == ItemStatus.SUBSTITUTE) {
                    matchCount++;
                    // Always count their weight towards replicaScore
                    double w = item.getRebalancedWeight() != null ? item.getRebalancedWeight() : (item.getEtfWeight() != null ? item.getEtfWeight() : 0.0);
                    item.setReplicaWeight(BasketUtils.round(w));
                    replicaTotal += item.getReplicaWeight();
                } else if (item.getBuyQuantity() != null && item.getBuyQuantity() > 0 
                           && item.getLastPrice() != null) {
                    // Recalculate replicaWeight based on actual purchased value for missing items
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
            opportunity.setReadyToReplicate(replicaTotal >= 90.0);
            opportunity.setHeldCount(matchCount);
            opportunity.setMissingCount(total - matchCount);
        }
        opportunity.setInvestmentAmount(investmentAmount);
        
        double heldScore = opportunity.getComposition().stream().filter(i -> i.getStatus() == ItemStatus.HELD)
                .mapToDouble(i -> i.getReplicaWeight() != null ? i.getReplicaWeight() : 0.0).sum();
        double subScore = opportunity.getComposition().stream().filter(i -> i.getStatus() == ItemStatus.SUBSTITUTE)
                .mapToDouble(i -> i.getReplicaWeight() != null ? i.getReplicaWeight() : 0.0).sum();
        opportunity.setHeldMatchScore(BasketUtils.round(heldScore));
        opportunity.setSubstituteMatchScore(BasketUtils.round(subScore));

        // Compute and return actual investment cost and budget variance
        double actualCost = opportunity.getComposition().stream()
                .filter(i -> i.getBuyQuantity() != null && i.getBuyQuantity() > 0
                        && i.getLastPrice() != null)
                .mapToDouble(i -> i.getBuyQuantity() * i.getLastPrice())
                .sum();
        opportunity.setActualInvestmentCost(BasketUtils.round(actualCost));
        opportunity.setBudgetVariance(BasketUtils.round(actualCost - investmentAmount));
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

            boolean isSectorial = false;
            if (etf.getHoldings() != null) {
                Map<String, Double> sectorWeights = new HashMap<>();
                for (EtfHolding h : etf.getHoldings()) {
                    if (h.getSector() != null && h.getWeight() > 0) {
                        String sector = SectorNormalizer.normalizeFine(h.getSector());
                        sectorWeights.put(sector, sectorWeights.getOrDefault(sector, 0.0) + h.getWeight());
                    }
                }
                for (Double weight : sectorWeights.values()) {
                    if (weight > 75.0) {
                        isSectorial = true;
                        break;
                    }
                }
            }

            BasketOpportunity opportunity = calculateOverlap(etfQuery, etf, userMap, userSectorMap, userHoldings, isSectorial);
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

        // Determine if the ETF is Sectorial (e.g., > 75% weight in a single sector)
        boolean isSectorial = false;
        if (etf.getHoldings() != null) {
            Map<String, Double> sectorWeights = new HashMap<>();
            for (EtfHolding h : etf.getHoldings()) {
                if (h.getSector() != null && h.getWeight() > 0) {
                    String sector = SectorNormalizer.normalizeFine(h.getSector());
                    sectorWeights.put(sector, sectorWeights.getOrDefault(sector, 0.0) + h.getWeight());
                }
            }
            for (Double weight : sectorWeights.values()) {
                if (weight > 75.0) {
                    isSectorial = true;
                    break;
                }
            }
        }

        BasketOpportunity opp = calculateOverlap(etfIsin, etf, userMap, userSectorMap, userHoldings, isSectorial);

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
            boolean isSectorial) {

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
                
                boolean handled = processSectorSubstitute(pair.item, pair.req, userSectorMap, consumedWeightByIsin, allUserHoldings, prices, isSectorial);
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
            boolean isSectorial) {
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
                
        // Add Tier 3 (Cross Sector) only if Tiers 1 and 2 are empty or very sparse, AND not a Sectorial ETF
        if (!isSectorial && alts.isEmpty() && allUserHoldings != null) {
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

        Map<String, List<SubstituteAssignment>> assignmentsByMissing = assignments.stream()
                .filter(a -> a.getMissingIsin() != null && !a.getMissingIsin().isBlank())
                .collect(Collectors.groupingBy(SubstituteAssignment::getMissingIsin));

        List<String> warnings = new ArrayList<>();
        List<BasketItem> newComposition = new ArrayList<>();

        for (BasketItem originalItem : base.getComposition()) {
            String missingKey = originalItem.getIsin();
            if (missingKey == null) missingKey = originalItem.getStockSymbol();
            
            List<SubstituteAssignment> itemAssignments = assignmentsByMissing.get(originalItem.getIsin());
            if (itemAssignments == null) itemAssignments = assignmentsByMissing.get(originalItem.getStockSymbol());

            if (itemAssignments == null || itemAssignments.isEmpty() || originalItem.getStatus() == ItemStatus.HELD) {
                newComposition.add(originalItem);
                continue;
            }

            double remainingEtfWeight = originalItem.getEtfWeight() != null ? originalItem.getEtfWeight() : 0.0;
            
            // Free previous auto-sub if flipping from SUBSTITUTE
            if (originalItem.getStatus() == ItemStatus.SUBSTITUTE && originalItem.getUserHoldingIsin() != null) {
                consumedWeightByIsin.computeIfPresent(originalItem.getUserHoldingIsin(), 
                    (k, v) -> Math.max(0, v - (originalItem.getReplicaWeight() != null ? originalItem.getReplicaWeight() : 0.0)));
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

                String resolvedIsin = sub.getIsin() != null ? sub.getIsin() : substituteKey;
                double subWeight = getAvailableWeight(sub);
                
                double consumed = consumedWeightByIsin.getOrDefault(resolvedIsin, 0.0);
                double availableSubWeight = subWeight - consumed;

                if (availableSubWeight < 0.01) {
                    log.warn("Substitute ISIN fully consumed: {}", resolvedIsin);
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
        base.setReplicaScore(BasketUtils.round(replica));
        base.setReadyToReplicate(replica >= 90.0);
        base.setHeldCount(heldCount);
        base.setMissingCount(total - matchCount);
        base.setTotalItems(total);
        base.setBuyList(buyList);

        return base;
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
