package com.portfolio.basket.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.basket.client.EtfApiClient;
import com.portfolio.basket.model.BasketOpportunity;
import com.portfolio.basket.model.BasketOpportunity.BasketItem;
import com.portfolio.basket.model.BasketOpportunity.ItemStatus;
import com.portfolio.model.basket.ExposureResponse;
import com.portfolio.model.basket.ExposureResponse.EtfExposureSource;
import com.portfolio.model.basket.ExposureResponse.SectorExposure;
import com.portfolio.model.basket.ExposureResponse.StockExposure;
import com.portfolio.model.portfolio.EquityHoldings;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BasketEngineService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EtfApiClient etfApiClient;

    // Cache for ETF Data
    private Map<String, EtfData> etfDataMap = new HashMap<>();

    @PostConstruct
    public void init() {
        // Load Initial Mock Data (Optional, kept as fallback)
        loadMockData();
    }

    private void loadMockData() {
        try {
            ClassPathResource resource = new ClassPathResource("mocks/etf_bulk_holdings.json");
            if (resource.exists()) {
                etfDataMap = objectMapper.readValue(resource.getInputStream(),
                        new TypeReference<Map<String, EtfData>>() {
                        });
                log.info("✅ Loaded {} ETF mocks (Fallback)", etfDataMap.size());
            }
        } catch (IOException e) {
            log.error("❌ Failed to load mock ETF data", e);
        }
    }

    public List<BasketOpportunity> findOpportunities(List<EquityHoldings> userHoldings, String etfQuery) {
        // 0. Calculate User Portfolio Weights
        calculateUserWeights(userHoldings);

        // 1. Discover ETFs
        Set<String> allIsins = new HashSet<>();

        if (etfQuery != null && !etfQuery.trim().isEmpty()) {
            log.info("Discovering ETFs via search query: {}", etfQuery);
            allIsins.addAll(etfApiClient.searchEtfs(etfQuery));
        }

        // Add cached ones (fallback or supplement)
        if (allIsins.isEmpty()) {
            log.info("No search results or no query provided. Using cached/mock ISINs.");
            allIsins.addAll(etfDataMap.keySet());
        }

        if (allIsins.isEmpty()) {
            log.warn("No ETFs discovered for matching.");
            return Collections.emptyList();
        }

        log.info("Processing {} ETFs for matching", allIsins.size());
        List<BasketOpportunity> opportunities = findOpportunitiesInternal(userHoldings, allIsins);

        // 2. Sort by match score descending
        opportunities.sort(Comparator.comparingDouble(BasketOpportunity::getMatchScore).reversed());

        return opportunities;
    }

    /**
     * Feature 1: Cumulative Look-Through Exposure
     * Calculates total exposure to each stock and sector by aggregating direct
     * holdings
     * and indirect exposure via ETFs. Each stock exposure includes a detailed
     * breakdown of sources.
     */
    public ExposureResponse calculateCumulativeExposure(List<EquityHoldings> userHoldings) {
        // 0. Ensure weights are calculated
        calculateUserWeights(userHoldings);

        Map<String, StockExposure> stockExposureMap = new HashMap<>();
        Map<String, Double> sectorExposureMap = new HashMap<>();

        for (EquityHoldings holding : userHoldings) {
            String isin = holding.getIsin();
            double holdingWeight = holding.getWeightInPortfolio();

            // 1. Try to fetch ETF constituents (Look-through)
            EtfData etfDetails = getEtfData(isin);
            if (etfDetails != null && etfDetails.getHoldings() != null) {
                log.info("Look-through for ETF holding: {} ({})", etfDetails.getName(), isin);
                for (EtfHolding constituent : etfDetails.getHoldings()) {
                    // Indirect Weight = (ETF Weight in Portfolio * Stock Weight in ETF) / 100
                    double indirectWeight = (holdingWeight * constituent.getWeight()) / 100.0;

                    StockExposure exposure = stockExposureMap.computeIfAbsent(constituent.getIsin(),
                            k -> StockExposure.builder()
                                    .isin(constituent.getIsin())
                                    .symbol(constituent.getSymbol())
                                    .sector(constituent.getSector())
                                    .sources(new ArrayList<>())
                                    .build());

                    exposure.setIndirectWeight(round(exposure.getIndirectWeight() + indirectWeight));
                    exposure.setTotalWeight(round(exposure.getTotalWeight() + indirectWeight));
                    exposure.getSources().add(EtfExposureSource.builder()
                            .etfIsin(isin)
                            .etfSymbol(etfDetails.getSymbol())
                            .contribution(round(indirectWeight))
                            .build());

                    // Aggregate sector exposure
                    String sector = constituent.getSector() != null ? constituent.getSector() : "Unknown";
                    sectorExposureMap.put(sector, sectorExposureMap.getOrDefault(sector, 0.0) + indirectWeight);
                }
            } else {
                // 2. Direct Stock Exposure
                StockExposure exposure = stockExposureMap.computeIfAbsent(isin, k -> StockExposure.builder()
                        .isin(isin)
                        .symbol(holding.getSymbol())
                        .sector(holding.getSector())
                        .sources(new ArrayList<>())
                        .build());

                exposure.setDirectWeight(round(exposure.getDirectWeight() + holdingWeight));
                exposure.setTotalWeight(round(exposure.getTotalWeight() + holdingWeight));

                // Add source attribution for direct holding with portfolio context
                exposure.getSources().add(EtfExposureSource.builder()
                        .etfIsin(null) // No ETF for direct holdings
                        .etfSymbol(null) // No ETF symbol for direct holdings
                        .portfolioId(holding.getPortfolioId()) // Actual portfolio UUID
                        .portfolioName(holding.getPortfolioName()) // Actual portfolio name
                        .contribution(round(holdingWeight))
                        .build());

                // Aggregate sector exposure
                String sector = holding.getSector() != null ? holding.getSector() : "Unknown";
                sectorExposureMap.put(sector, sectorExposureMap.getOrDefault(sector, 0.0) + holdingWeight);
            }
        }

        List<StockExposure> sortedStockExposure = new ArrayList<>(stockExposureMap.values());
        sortedStockExposure.sort(Comparator.comparingDouble(StockExposure::getTotalWeight).reversed());

        List<SectorExposure> sortedSectorExposure = sectorExposureMap.entrySet().stream()
                .map(e -> SectorExposure.builder().sector(e.getKey()).weight(round(e.getValue())).build())
                .sorted(Comparator.comparingDouble(SectorExposure::getWeight).reversed())
                .collect(Collectors.toList());

        return ExposureResponse.builder()
                .stockExposure(sortedStockExposure)
                .sectorExposure(sortedSectorExposure)
                .build();
    }

    // Helper to calculate user weights dynamically
    private void calculateUserWeights(List<EquityHoldings> userHoldings) {
        if (userHoldings == null || userHoldings.isEmpty())
            return;

        // Calculate total value using Current Value (preferred) or Investment Cost
        // (fallback)
        double totalValue = userHoldings.stream()
                .mapToDouble(h -> {
                    if (h.getCurrentValue() != null)
                        return h.getCurrentValue();
                    if (h.getInvestmentCost() != null)
                        return h.getInvestmentCost();
                    return 0.0;
                })
                .sum();

        if (totalValue > 0) {
            userHoldings.forEach(h -> {
                double value = 0.0;
                if (h.getCurrentValue() != null) {
                    value = h.getCurrentValue();
                } else if (h.getInvestmentCost() != null) {
                    value = h.getInvestmentCost();
                }

                h.setWeightInPortfolio((value / totalValue) * 100.0);
            });
        }
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

    // Fetch Data with Mock Fallback
    public EtfData getEtfData(String isin) {
        // 1. Try Live API
        EtfData data = etfApiClient.fetchEtfHoldings(isin);
        if (data != null) {
            etfDataMap.put(isin, data); // Update cache with live data
            return data;
        }

        // 2. Fallback to Mock / Cache
        if (etfDataMap.containsKey(isin)) {
            log.warn("⚠️ Using Mock/Cached data for {}", isin);
            return etfDataMap.get(isin);
        }

        return null;
    }

    // Called by Controller for specific preview
    public BasketOpportunity getPreview(String etfIsin, List<EquityHoldings> userHoldings) {
        // 0. Calculate User Weights
        calculateUserWeights(userHoldings);

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
                        .build();

                // A. Direct Match
                if (req.getIsin() != null && userMap.containsKey(req.getIsin())) {
                    EquityHoldings userHolding = userMap.get(req.getIsin());
                    item.setStatus(ItemStatus.HELD);
                    item.setUserHoldingSymbol(userHolding.getSymbol());
                    item.setUserWeight(round(userHolding.getWeightInPortfolio()));

                    // Match weight = min(etfWeight, userWeight)
                    double matchWeight = Math.min(req.getWeight(), userHolding.getWeightInPortfolio());
                    item.setReplicaWeight(round(matchWeight));
                    replicaScoreTotal += matchWeight;
                    matchCount++;
                }
                // B. Sector Substitution
                else {
                    List<EquityHoldings> sectorPeers = userSectorMap.getOrDefault(req.getSector(),
                            Collections.emptyList());

                    if (!sectorPeers.isEmpty()) {
                        // Populate alternatives list
                        List<BasketOpportunity.Alternative> alts = sectorPeers.stream()
                                .map(h -> BasketOpportunity.Alternative.builder()
                                        .symbol(h.getSymbol())
                                        .isin(h.getIsin())
                                        .userWeight(round(h.getWeightInPortfolio()))
                                        .build())
                                .collect(Collectors.toList());
                        item.setAlternatives(alts);

                        // Pick best substitute (simplistic: highest user weight in that sector)
                        EquityHoldings substitute = sectorPeers.stream()
                                .max(Comparator.comparingDouble(EquityHoldings::getWeightInPortfolio))
                                .orElse(sectorPeers.get(0));

                        item.setStatus(ItemStatus.SUBSTITUTE);
                        item.setUserHoldingSymbol(substitute.getSymbol());
                        item.setUserWeight(round(substitute.getWeightInPortfolio()));
                        item.setReason("Sector Match: " + req.getSector());

                        // For substitute, we assume it fills the weight requirement of the ETF stock
                        double matchWeight = Math.min(req.getWeight(), substitute.getWeightInPortfolio());
                        item.setReplicaWeight(round(matchWeight));
                        replicaScoreTotal += matchWeight;
                        matchCount++;
                    } else {
                        item.setStatus(ItemStatus.MISSING);
                        item.setUserWeight(0.0);
                        item.setBuyQuantity(1.0); // Placeholder for quantity
                        buyList.add(item);
                    }
                }
                composition.add(item);
            }
        }

        double matchScore = (total == 0) ? 0 : (double) matchCount / total * 100.0;
        double replicaScore = replicaScoreTotal; // Aggregated weight match

        return BasketOpportunity.builder()
                .etfIsin(etfIsin)
                .etfName(etf.getName())
                .matchScore(round(matchScore))
                .replicaScore(round(replicaScore))
                .readyToReplicate(replicaScore >= 70.0)
                .totalItems(total)
                .heldCount(matchCount)
                .missingCount(total - matchCount)
                .composition(composition)
                .buyList(buyList)
                .build();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Generate comprehensive portfolio allocation for UI visualization
     * Includes stock counts, sector breakdown, and direct/indirect analysis
     */
    public com.portfolio.model.basket.PortfolioAllocationResponse calculatePortfolioAllocation(
            List<EquityHoldings> userHoldings) {
        // 0. Calculate weights
        calculateUserWeights(userHoldings);

        // Get exposure data first
        ExposureResponse exposure = calculateCumulativeExposure(userHoldings);

        // Build comprehensive allocation response
        return buildAllocationResponse(exposure, userHoldings);
    }

    private com.portfolio.model.basket.PortfolioAllocationResponse buildAllocationResponse(
            ExposureResponse exposure, List<EquityHoldings> userHoldings) {

        // Calculate overview stats
        int totalStocks = exposure.getStockExposure() != null ? exposure.getStockExposure().size() : 0;
        int directStockCount = 0;
        int indirectStockCount = 0;
        double totalDirectPercentage = 0.0;
        double totalIndirectPercentage = 0.0;

        // Track portfolios and sources
        Map<String, com.portfolio.model.basket.PortfolioAllocationResponse.PortfolioContribution> portfolioContributions = new HashMap<>();
        Map<String, com.portfolio.model.basket.PortfolioAllocationResponse.IndirectAllocation> indirectSources = new HashMap<>();

        // Process stock allocations
        List<com.portfolio.model.basket.PortfolioAllocationResponse.StockAllocation> stockAllocations = new ArrayList<>();

        if (exposure.getStockExposure() != null) {
            for (StockExposure stock : exposure.getStockExposure()) {
                List<com.portfolio.model.basket.PortfolioAllocationResponse.AllocationSource> sources = new ArrayList<>();

                Set<String> processedSourceIds = new HashSet<>();

                if (stock.getSources() != null) {
                    for (EtfExposureSource source : stock.getSources()) {
                        com.portfolio.model.basket.PortfolioAllocationResponse.SourceType sourceType;
                        String sourceId;
                        String sourceName;

                        // Determine source type and ID
                        if (source.getPortfolioId() != null) {
                            sourceType = com.portfolio.model.basket.PortfolioAllocationResponse.SourceType.DIRECT_PORTFOLIO;
                            sourceId = source.getPortfolioId();
                            sourceName = source.getPortfolioName();
                        } else if (source.getEtfIsin() != null) {
                            sourceType = com.portfolio.model.basket.PortfolioAllocationResponse.SourceType.ETF;
                            sourceId = source.getEtfIsin();
                            sourceName = source.getEtfSymbol();
                        } else {
                            continue;
                        }

                        // Track portfolio contribution
                        if (source.getPortfolioId() != null) {
                            portfolioContributions.computeIfAbsent(sourceId,
                                    k -> com.portfolio.model.basket.PortfolioAllocationResponse.PortfolioContribution
                                            .builder()
                                            .portfolioId(sourceId)
                                            .portfolioName(sourceName)
                                            .percentage(0.0)
                                            .stockCount(0)
                                            .build());
                            com.portfolio.model.basket.PortfolioAllocationResponse.PortfolioContribution contrib = portfolioContributions
                                    .get(sourceId);
                            contrib.setPercentage(round(contrib.getPercentage() + source.getContribution()));
                            // Only increment stock count if we haven't seen this source for this stock yet
                            if (processedSourceIds.add(sourceId)) {
                                contrib.setStockCount(contrib.getStockCount() + 1);
                            }
                        }
                        // Track indirect source
                        else if (source.getEtfIsin() != null) {
                            indirectSources.computeIfAbsent(sourceId,
                                    k -> com.portfolio.model.basket.PortfolioAllocationResponse.IndirectAllocation
                                            .builder()
                                            .sourceType(sourceType)
                                            .sourceId(sourceId)
                                            .sourceName(sourceName)
                                            .percentage(0.0)
                                            .stockCount(0)
                                            .build());
                            com.portfolio.model.basket.PortfolioAllocationResponse.IndirectAllocation indirect = indirectSources
                                    .get(sourceId);
                            indirect.setPercentage(round(indirect.getPercentage() + source.getContribution()));
                            // Only increment stock count if we haven't seen this source for this stock yet
                            if (processedSourceIds.add(sourceId)) {
                                indirect.setStockCount(indirect.getStockCount() + 1);
                            }
                        }

                        sources.add(com.portfolio.model.basket.PortfolioAllocationResponse.AllocationSource.builder()
                                .sourceType(sourceType)
                                .sourceId(sourceId)
                                .sourceName(sourceName)
                                .contribution(round(source.getContribution()))
                                .build());
                    }
                }

                // Count unique stocks - each stock counted only once
                // If stock has any direct weight, it's a "direct" stock
                // Only count as "indirect" if it has NO direct weight
                if (stock.getDirectWeight() > 0) {
                    directStockCount++;
                    totalDirectPercentage += stock.getDirectWeight();
                } else if (stock.getIndirectWeight() > 0) {
                    // Only count as indirect if there's NO direct weight
                    indirectStockCount++;
                    totalIndirectPercentage += stock.getIndirectWeight();
                }

                // Add indirect percentage to total even for direct stocks
                if (stock.getDirectWeight() > 0 && stock.getIndirectWeight() > 0) {
                    totalIndirectPercentage += stock.getIndirectWeight();
                }

                stockAllocations.add(com.portfolio.model.basket.PortfolioAllocationResponse.StockAllocation.builder()
                        .isin(stock.getIsin())
                        .symbol(stock.getSymbol())
                        .sector(stock.getSector())
                        .totalPercentage(round(stock.getTotalWeight()))
                        .directPercentage(round(stock.getDirectWeight()))
                        .indirectPercentage(round(stock.getIndirectWeight()))
                        .sources(sources.isEmpty() ? null : sources)
                        .build());
            }
        }

        // Process sector allocations
        List<com.portfolio.model.basket.PortfolioAllocationResponse.SectorAllocation> sectorAllocations = new ArrayList<>();
        Map<String, com.portfolio.model.basket.PortfolioAllocationResponse.SectorAllocation> sectorMap = new HashMap<>();

        if (exposure.getStockExposure() != null) {
            for (StockExposure stock : exposure.getStockExposure()) {
                String sector = stock.getSector() != null ? stock.getSector() : "Unknown";

                com.portfolio.model.basket.PortfolioAllocationResponse.SectorAllocation sectorAlloc = sectorMap
                        .computeIfAbsent(sector,
                                k -> com.portfolio.model.basket.PortfolioAllocationResponse.SectorAllocation.builder()
                                        .sectorName(sector)
                                        .totalPercentage(0.0)
                                        .directPercentage(0.0)
                                        .indirectPercentage(0.0)
                                        .stockCount(0)
                                        .topStocks(new ArrayList<>())
                                        .build());

                sectorAlloc.setTotalPercentage(round(sectorAlloc.getTotalPercentage() + stock.getTotalWeight()));
                sectorAlloc.setDirectPercentage(round(sectorAlloc.getDirectPercentage() + stock.getDirectWeight()));
                sectorAlloc
                        .setIndirectPercentage(round(sectorAlloc.getIndirectPercentage() + stock.getIndirectWeight()));
                sectorAlloc.setStockCount(sectorAlloc.getStockCount() + 1);

                // Add to top stocks list (limit to 5)
                if (sectorAlloc.getTopStocks().size() < 5) {
                    sectorAlloc.getTopStocks().add(stock.getSymbol());
                }
            }

            sectorAllocations = sectorMap.values().stream()
                    .sorted(Comparator.comparingDouble(
                            com.portfolio.model.basket.PortfolioAllocationResponse.SectorAllocation::getTotalPercentage)
                            .reversed())
                    .collect(Collectors.toList());
        }

        // Build overview
        com.portfolio.model.basket.PortfolioAllocationResponse.AllocationOverview overview = com.portfolio.model.basket.PortfolioAllocationResponse.AllocationOverview
                .builder()
                .totalStocks(totalStocks)
                .totalDirectPercentage(round(totalDirectPercentage))
                .totalIndirectPercentage(round(totalIndirectPercentage))
                .directStockCount(directStockCount)
                .indirectStockCount(indirectStockCount)
                .totalSectors(sectorAllocations.size())
                .build();

        // Build direct/indirect breakdown
        com.portfolio.model.basket.PortfolioAllocationResponse.DirectAllocation directAllocation = com.portfolio.model.basket.PortfolioAllocationResponse.DirectAllocation
                .builder()
                .totalPercentage(round(totalDirectPercentage))
                .stockCount(directStockCount)
                .portfolioContributions(new ArrayList<>(portfolioContributions.values()))
                .build();

        com.portfolio.model.basket.PortfolioAllocationResponse.DirectIndirectBreakdown breakdown = com.portfolio.model.basket.PortfolioAllocationResponse.DirectIndirectBreakdown
                .builder()
                .directAllocation(directAllocation)
                .indirectAllocations(new ArrayList<>(indirectSources.values()))
                .build();

        // Build final response
        return com.portfolio.model.basket.PortfolioAllocationResponse.builder()
                .userId(exposure.getUserId())
                .portfolioId(exposure.getPortfolioId())
                .overview(overview)
                .stockAllocations(stockAllocations)
                .sectorAllocations(sectorAllocations)
                .directIndirectBreakdown(breakdown)
                .build();
    }

    @Data
    public static class EtfData {
        private String symbol;
        private String name;
        private List<EtfHolding> holdings;
    }

    @Data
    public static class EtfHolding {
        private String isin;
        private String symbol;
        private String sector;
        private double weight;
    }
}
