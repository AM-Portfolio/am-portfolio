package com.portfolio.basket.service;

import com.portfolio.basket.client.EtfApiClient;
import com.portfolio.basket.model.EtfData;
import com.portfolio.basket.model.EtfHolding;
import com.portfolio.basket.util.BasketUtils;
import com.portfolio.model.basket.ExposureResponse;
import com.portfolio.model.basket.ExposureResponse.EtfExposureSource;
import com.portfolio.model.basket.ExposureResponse.SectorExposure;
import com.portfolio.model.basket.ExposureResponse.StockExposure;
import com.portfolio.model.portfolio.EquityHoldings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BasketAllocationService {

    private final EtfApiClient etfApiClient;

    /**
     * Feature 1: Cumulative Look-Through Exposure
     * Calculates total exposure to each stock and sector by aggregating direct
     * holdings
     * and indirect exposure via ETFs. Each stock exposure includes a detailed
     * breakdown of sources.
     */
    public ExposureResponse calculateCumulativeExposure(List<EquityHoldings> userHoldings) {
        // 0. Ensure weights are calculated
        BasketUtils.calculateUserWeights(userHoldings);

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

                    exposure.setIndirectWeight(BasketUtils.round(exposure.getIndirectWeight() + indirectWeight));
                    exposure.setTotalWeight(BasketUtils.round(exposure.getTotalWeight() + indirectWeight));
                    exposure.getSources().add(EtfExposureSource.builder()
                            .etfIsin(isin)
                            .etfSymbol(etfDetails.getSymbol())
                            .contribution(BasketUtils.round(indirectWeight))
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

                exposure.setDirectWeight(BasketUtils.round(exposure.getDirectWeight() + holdingWeight));
                exposure.setTotalWeight(BasketUtils.round(exposure.getTotalWeight() + holdingWeight));

                // Add source attribution for direct holding with portfolio context
                exposure.getSources().add(EtfExposureSource.builder()
                        .etfIsin(null) // No ETF for direct holdings
                        .etfSymbol(null) // No ETF symbol for direct holdings
                        .portfolioId(holding.getPortfolioId()) // Actual portfolio UUID
                        .portfolioName(holding.getPortfolioName()) // Actual portfolio name
                        .contribution(BasketUtils.round(holdingWeight))
                        .build());

                // Aggregate sector exposure
                String sector = holding.getSector() != null ? holding.getSector() : "Unknown";
                sectorExposureMap.put(sector, sectorExposureMap.getOrDefault(sector, 0.0) + holdingWeight);
            }
        }

        List<StockExposure> sortedStockExposure = new ArrayList<>(stockExposureMap.values());
        sortedStockExposure.sort(Comparator.comparingDouble(StockExposure::getTotalWeight).reversed());

        List<SectorExposure> sortedSectorExposure = sectorExposureMap.entrySet().stream()
                .map(e -> SectorExposure.builder().sector(e.getKey()).weight(BasketUtils.round(e.getValue())).build())
                .sorted(Comparator.comparingDouble(SectorExposure::getWeight).reversed())
                .collect(Collectors.toList());

        return ExposureResponse.builder()
                .stockExposure(sortedStockExposure)
                .sectorExposure(sortedSectorExposure)
                .build();
    }

    /**
     * Generate comprehensive portfolio allocation for UI visualization
     * Includes stock counts, sector breakdown, and direct/indirect analysis
     */
    public com.portfolio.model.basket.PortfolioAllocationResponse calculatePortfolioAllocation(
            List<EquityHoldings> userHoldings) {
        // 0. Calculate weights
        BasketUtils.calculateUserWeights(userHoldings);

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
                            contrib.setPercentage(
                                    BasketUtils.round(contrib.getPercentage() + source.getContribution()));
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
                            indirect.setPercentage(
                                    BasketUtils.round(indirect.getPercentage() + source.getContribution()));
                            // Only increment stock count if we haven't seen this source for this stock yet
                            if (processedSourceIds.add(sourceId)) {
                                indirect.setStockCount(indirect.getStockCount() + 1);
                            }
                        }

                        sources.add(com.portfolio.model.basket.PortfolioAllocationResponse.AllocationSource.builder()
                                .sourceType(sourceType)
                                .sourceId(sourceId)
                                .sourceName(sourceName)
                                .contribution(BasketUtils.round(source.getContribution()))
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
                        .totalPercentage(BasketUtils.round(stock.getTotalWeight()))
                        .directPercentage(BasketUtils.round(stock.getDirectWeight()))
                        .indirectPercentage(BasketUtils.round(stock.getIndirectWeight()))
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

                sectorAlloc.setTotalPercentage(
                        BasketUtils.round(sectorAlloc.getTotalPercentage() + stock.getTotalWeight()));
                sectorAlloc.setDirectPercentage(
                        BasketUtils.round(sectorAlloc.getDirectPercentage() + stock.getDirectWeight()));
                sectorAlloc
                        .setIndirectPercentage(
                                BasketUtils.round(sectorAlloc.getIndirectPercentage() + stock.getIndirectWeight()));
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
                .totalDirectPercentage(BasketUtils.round(totalDirectPercentage))
                .totalIndirectPercentage(BasketUtils.round(totalIndirectPercentage))
                .directStockCount(directStockCount)
                .indirectStockCount(indirectStockCount)
                .totalSectors(sectorAllocations.size())
                .build();

        // Build direct/indirect breakdown
        com.portfolio.model.basket.PortfolioAllocationResponse.DirectAllocation directAllocation = com.portfolio.model.basket.PortfolioAllocationResponse.DirectAllocation
                .builder()
                .totalPercentage(BasketUtils.round(totalDirectPercentage))
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

    // Fetch Data from Live API Only (Shared with Engine Service but can be reused
    // here)
    // Ideally this should be in EtfApiClient or a DataService, but keeping here for
    // now as private helper
    private EtfData getEtfData(String isin) {
        // log.info("Fetching live ETF data for ISIN: {}", isin); // Use TRACE log if
        // needed
        EtfData data = etfApiClient.fetchEtfHoldings(isin);
        if (data != null) {
            etfApiClient.enrichHoldings(data.getHoldings());
        }
        return data;
    }
}
