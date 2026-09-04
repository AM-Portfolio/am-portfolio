package com.portfolio.basket.service;

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
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BasketAllocationService {

    private final EnrichedEtfService enrichedEtfService;

    /**
     * Cumulative look-through exposure.
     * Uses one {@link EnrichedEtfService#getEnrichedEtfsBatch} for all distinct
     * holding ISINs/symbols (no N+1 am-parser calls). Non-ETF / empty batch
     * entries fall back to direct stock exposure (same as before).
     */
    public ExposureResponse calculateCumulativeExposure(List<EquityHoldings> userHoldings) {
        BasketUtils.calculateUserWeights(userHoldings);

        Map<String, StockExposure> stockExposureMap = new HashMap<>();
        Map<String, Double> sectorExposureMap = new HashMap<>();

        Map<String, EtfData> etfByQuery = loadEtfLookThroughBatch(userHoldings);
        log.info("exposure.etf_batch size={} holdings={}", etfByQuery.size(),
                userHoldings != null ? userHoldings.size() : 0);

        for (EquityHoldings holding : userHoldings) {
            String isin = holding.getIsin();
            double holdingWeight = holding.getWeightInPortfolio();

            EtfData etfDetails = resolveEtfFromBatch(etfByQuery, holding);
            if (etfDetails != null
                    && etfDetails.getHoldings() != null
                    && !etfDetails.getHoldings().isEmpty()) {
                log.debug("Look-through for ETF holding: {} ({})", etfDetails.getName(), isin);
                for (EtfHolding constituent : etfDetails.getHoldings()) {
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

                    String sector = constituent.getSector() != null ? constituent.getSector() : "Unknown";
                    sectorExposureMap.put(sector, sectorExposureMap.getOrDefault(sector, 0.0) + indirectWeight);
                }
            } else {
                StockExposure exposure = stockExposureMap.computeIfAbsent(isin, k -> StockExposure.builder()
                        .isin(isin)
                        .symbol(holding.getSymbol())
                        .sector(holding.getSector())
                        .sources(new ArrayList<>())
                        .build());

                exposure.setDirectWeight(BasketUtils.round(exposure.getDirectWeight() + holdingWeight));
                exposure.setTotalWeight(BasketUtils.round(exposure.getTotalWeight() + holdingWeight));

                exposure.getSources().add(EtfExposureSource.builder()
                        .etfIsin(null)
                        .etfSymbol(null)
                        .portfolioId(holding.getPortfolioId())
                        .portfolioName(holding.getPortfolioName())
                        .contribution(BasketUtils.round(holdingWeight))
                        .build());

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
     * Portfolio allocation for UI — reuses a single exposure compute (no second ETF batch).
     */
    public com.portfolio.model.basket.PortfolioAllocationResponse calculatePortfolioAllocation(
            List<EquityHoldings> userHoldings) {
        BasketUtils.calculateUserWeights(userHoldings);
        ExposureResponse exposure = calculateCumulativeExposure(userHoldings);
        return buildAllocationResponse(exposure, userHoldings);
    }

    /**
     * One am-parser / cache batch for holdings that look like ETFs (INF* ISIN or
     * *BEES/*ETF symbol). Equity (INE*) rows stay direct-stock — no am-parser spam.
     */
    Map<String, EtfData> loadEtfLookThroughBatch(List<EquityHoldings> userHoldings) {
        if (userHoldings == null || userHoldings.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        for (EquityHoldings h : userHoldings) {
            if (!isLikelyEtfHolding(h)) {
                continue;
            }
            if (h.getIsin() != null && !h.getIsin().isBlank()) {
                queries.add(h.getIsin().trim());
            } else if (h.getSymbol() != null && !h.getSymbol().isBlank()) {
                queries.add(h.getSymbol().trim());
            }
        }
        if (queries.isEmpty()) {
            log.info("exposure.etf_batch skipped — no ETF-like holdings among {}", userHoldings.size());
            return Collections.emptyMap();
        }
        long start = System.currentTimeMillis();
        Map<String, EtfData> batch = enrichedEtfService.getEnrichedEtfsBatch(new ArrayList<>(queries));
        log.info("exposure.etf_batch.done queries={} resolved={} durationMs={}",
                queries.size(),
                batch != null ? batch.size() : 0,
                System.currentTimeMillis() - start);
        return batch != null ? batch : Collections.emptyMap();
    }

    static boolean isLikelyEtfHolding(EquityHoldings h) {
        if (h == null) {
            return false;
        }
        if (h.getIsin() != null && !h.getIsin().isBlank()) {
            String isin = h.getIsin().trim().toUpperCase(Locale.ROOT);
            if (isin.startsWith("INF")) {
                return true;
            }
            // Equity ISINs (INE*) are not ETF look-through candidates
            if (isin.startsWith("INE")) {
                return false;
            }
        }
        if (h.getSymbol() != null && !h.getSymbol().isBlank()) {
            String sym = h.getSymbol().trim().toUpperCase(Locale.ROOT);
            return sym.endsWith("BEES") || sym.endsWith("ETF") || sym.contains("BEES");
        }
        return false;
    }

    static EtfData resolveEtfFromBatch(Map<String, EtfData> batch, EquityHoldings holding) {
        if (batch == null || batch.isEmpty() || holding == null) {
            return null;
        }
        if (holding.getIsin() != null && !holding.getIsin().isBlank()) {
            String isin = holding.getIsin().trim();
            EtfData byIsin = batch.get(isin);
            if (byIsin == null) {
                byIsin = batch.get(isin.toUpperCase(Locale.ROOT));
            }
            if (byIsin != null) {
                return byIsin;
            }
        }
        if (holding.getSymbol() != null && !holding.getSymbol().isBlank()) {
            String sym = holding.getSymbol().trim();
            EtfData bySym = batch.get(sym);
            if (bySym == null) {
                bySym = batch.get(sym.toUpperCase(Locale.ROOT));
            }
            return bySym;
        }
        return null;
    }

    private com.portfolio.model.basket.PortfolioAllocationResponse buildAllocationResponse(
            ExposureResponse exposure, List<EquityHoldings> userHoldings) {

        int totalStocks = exposure.getStockExposure() != null ? exposure.getStockExposure().size() : 0;
        int directStockCount = 0;
        int indirectStockCount = 0;
        double totalDirectPercentage = 0.0;
        double totalIndirectPercentage = 0.0;

        Map<String, com.portfolio.model.basket.PortfolioAllocationResponse.PortfolioContribution> portfolioContributions = new HashMap<>();
        Map<String, com.portfolio.model.basket.PortfolioAllocationResponse.IndirectAllocation> indirectSources = new HashMap<>();

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
                            if (processedSourceIds.add(sourceId)) {
                                contrib.setStockCount(contrib.getStockCount() + 1);
                            }
                        } else if (source.getEtfIsin() != null) {
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

                if (stock.getDirectWeight() > 0) {
                    directStockCount++;
                    totalDirectPercentage += stock.getDirectWeight();
                } else if (stock.getIndirectWeight() > 0) {
                    indirectStockCount++;
                    totalIndirectPercentage += stock.getIndirectWeight();
                }

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

        com.portfolio.model.basket.PortfolioAllocationResponse.AllocationOverview overview = com.portfolio.model.basket.PortfolioAllocationResponse.AllocationOverview
                .builder()
                .totalStocks(totalStocks)
                .totalDirectPercentage(BasketUtils.round(totalDirectPercentage))
                .totalIndirectPercentage(BasketUtils.round(totalIndirectPercentage))
                .directStockCount(directStockCount)
                .indirectStockCount(indirectStockCount)
                .totalSectors(sectorAllocations.size())
                .build();

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

        return com.portfolio.model.basket.PortfolioAllocationResponse.builder()
                .userId(exposure.getUserId())
                .portfolioId(exposure.getPortfolioId())
                .overview(overview)
                .stockAllocations(stockAllocations)
                .sectorAllocations(sectorAllocations)
                .directIndirectBreakdown(breakdown)
                .build();
    }
}
