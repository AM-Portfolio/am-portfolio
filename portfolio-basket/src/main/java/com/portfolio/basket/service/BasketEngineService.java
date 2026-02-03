package com.portfolio.basket.service;

import com.portfolio.basket.client.EtfApiClient;
import com.portfolio.basket.model.BasketOpportunity;
import com.portfolio.basket.model.BasketOpportunity.BasketItem;
import com.portfolio.basket.model.BasketOpportunity.ItemStatus;
import com.portfolio.basket.model.EtfData;
import com.portfolio.basket.model.EtfHolding;
import com.portfolio.basket.util.BasketUtils;
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
            if (price == null || price <= 0) {
                log.warn("Price not found for {}, skipping calculation", item.getStockSymbol());
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
        Set<String> allIsins = new HashSet<>();

        if (etfQuery != null && !etfQuery.trim().isEmpty()) {
            if (etfQuery.contains(",")) {
                log.info("Processing explicit ISIN list: {}", etfQuery);
                Arrays.stream(etfQuery.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .forEach(allIsins::add);
            } else {
                log.info("Discovering ETFs via search query: {}", etfQuery);
                allIsins.addAll(etfApiClient.searchEtfs(etfQuery));
            }
        }

        // No fallback - require valid search query or discovery mechanism
        if (allIsins.isEmpty()) {
            log.warn("No ETFs discovered. Query is required for ETF discovery.");
        }

        if (allIsins.isEmpty()) {
            log.warn("No ETFs discovered for matching.");
            return Collections.emptyList();
        }

        log.info("Processing {} ETFs for matching", allIsins.size());
        List<BasketOpportunity> opportunities = findOpportunitiesInternal(userHoldings, allIsins);

        // Set total portfolio value on each opportunity
        opportunities.forEach(op -> op.setTotalPortfolioValue(totalValue));

        // 2. Sort by match score descending
        opportunities.sort(Comparator.comparingDouble(BasketOpportunity::getMatchScore).reversed());

        return opportunities;
    }

    // Helper to calculate opportunities for specific ETF ISINs
    private List<BasketOpportunity> findOpportunitiesInternal(List<EquityHoldings> userHoldings, Set<String> etfIsins) {
        Map<String, EquityHoldings> userMap = userHoldings.stream()
                .collect(Collectors.toMap(EquityHoldings::getIsin, h -> h, (a, b) -> a));

        Map<String, List<EquityHoldings>> userSectorMap = userHoldings.stream()
                .filter(h -> h.getSector() != null)
                .collect(Collectors.groupingBy(EquityHoldings::getSector));

        List<BasketOpportunity> opportunities = new ArrayList<>();

        for (String etfIsin : etfIsins) {
            // Get Data (Cache or API)
            EtfData etf = getEtfData(etfIsin);
            if (etf == null)
                continue;

            BasketOpportunity opportunity = calculateOverlap(etfIsin, etf, userMap, userSectorMap);
            // Add all opportunities, sorting happens at top level
            opportunities.add(opportunity);
        }

        return opportunities;
    }

    // Fetch Data from Live API Only
    public EtfData getEtfData(String isin) {
        log.info("Fetching live ETF data for ISIN: {}", isin);
        EtfData data = etfApiClient.fetchEtfHoldings(isin);
        if (data == null) {
            log.warn("⚠️ No ETF data available from API for ISIN: {}", isin);
        } else {
            // Enrich with market data if possible
            log.info("Enriching ETF data for {}", isin);
            etfApiClient.enrichHoldings(data.getHoldings());
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
                .filter(h -> h.getSector() != null)
                .collect(Collectors.groupingBy(EquityHoldings::getSector));

        return calculateOverlap(etfIsin, etf, userMap, userSectorMap);
    }

    private BasketOpportunity calculateOverlap(String etfIsin, EtfData etf,
            Map<String, EquityHoldings> userMap,
            Map<String, List<EquityHoldings>> userSectorMap) {

        List<BasketItem> composition = new ArrayList<>();
        List<BasketItem> buyList = new ArrayList<>();
        double replicaScoreTotal = 0;
        int matchCount = 0;
        int total = etf.getHoldings() != null ? etf.getHoldings().size() : 0;

        if (etf.getHoldings() != null) {
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
                        .build();

                boolean isMatch = false;

                // A. Direct Match
                if (req.getIsin() != null && userMap.containsKey(req.getIsin())) {
                    isMatch = processDirectMatch(item, req, userMap.get(req.getIsin()));
                }
                // B. Sector Substitution (Enhanced with Market Cap)
                else {
                    isMatch = processSectorSubstitute(item, req, userSectorMap);
                    if (!isMatch) {
                        buyList.add(item); // Add to buy list only if no match/substitute found
                    }
                }

                if (isMatch) {
                    replicaScoreTotal += item.getReplicaWeight();
                    matchCount++;
                }

                composition.add(item);
            }
        }

        double matchScore = (total == 0) ? 0 : (double) matchCount / total * 100.0;
        double replicaScore = replicaScoreTotal; // Aggregated weight match

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

    private boolean processDirectMatch(BasketItem item, EtfHolding req, EquityHoldings userHolding) {
        log.info("Checking Held Item: {} | Qty: {} | AvgPrice: {}",
                userHolding.getSymbol(), userHolding.getQuantity(), userHolding.getAverageBuyingPrice());

        item.setStatus(ItemStatus.HELD);
        item.setUserHoldingSymbol(userHolding.getSymbol());
        item.setUserWeight(BasketUtils.round(userHolding.getWeightInPortfolio()));
        item.setHeldQuantity(userHolding.getQuantity());
        item.setHeldAveragePrice(userHolding.getAverageBuyingPrice());

        // Match weight = min(etfWeight, userWeight)
        double matchWeight = Math.min(req.getWeight(), userHolding.getWeightInPortfolio());
        item.setReplicaWeight(BasketUtils.round(matchWeight));

        return true;
    }

    private boolean processSectorSubstitute(BasketItem item, EtfHolding req,
            Map<String, List<EquityHoldings>> userSectorMap) {
        List<EquityHoldings> sectorPeers = userSectorMap.getOrDefault(req.getSector(), Collections.emptyList());

        // Filter peer candidates by Market Cap Category if available
        if (req.getMarketCapCategory() != null && !sectorPeers.isEmpty()) {
            List<EquityHoldings> mcapPeers = sectorPeers.stream()
                    .filter(p -> req.getMarketCapCategory().equalsIgnoreCase(p.getMarketCapCategory()))
                    .collect(Collectors.toList());
            if (!mcapPeers.isEmpty()) {
                sectorPeers = mcapPeers; // Refine candidates to those matching both Sector AND MCap
            }
        }

        if (!sectorPeers.isEmpty()) {
            // Populate alternatives list of substitutes
            List<BasketOpportunity.Alternative> alts = sectorPeers.stream()
                    .map(h -> BasketOpportunity.Alternative.builder()
                            .symbol(h.getSymbol())
                            .isin(h.getIsin())
                            .userWeight(BasketUtils.round(h.getWeightInPortfolio()))
                            .build())
                    .collect(Collectors.toList());
            item.setAlternatives(alts);

            // Pick best substitute (simplistic: highest user weight in that sector)
            EquityHoldings substitute = sectorPeers.stream()
                    .max(Comparator.comparingDouble(EquityHoldings::getWeightInPortfolio))
                    .orElse(sectorPeers.get(0));

            item.setStatus(ItemStatus.SUBSTITUTE);
            item.setUserHoldingSymbol(substitute.getSymbol());
            item.setUserWeight(BasketUtils.round(substitute.getWeightInPortfolio()));
            item.setHeldQuantity(substitute.getQuantity());
            item.setHeldAveragePrice(substitute.getAverageBuyingPrice());
            item.setReason("Substitute: " + req.getSector()
                    + (req.getMarketCapCategory() != null ? "/" + req.getMarketCapCategory() : ""));

            // For substitute, we assume it fills the weight requirement of the ETF stock
            double matchWeight = Math.min(req.getWeight(), substitute.getWeightInPortfolio());
            item.setReplicaWeight(BasketUtils.round(matchWeight));

            return true;
        } else {
            item.setStatus(ItemStatus.MISSING);
            item.setUserWeight(0.0);
            item.setBuyQuantity(1.0); // Placeholder for quantity
            return false;
        }
    }
}
