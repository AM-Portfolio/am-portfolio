package com.portfolio.analytics.service.providers.portfolio;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.analytics.model.AnalyticsType;
import com.portfolio.analytics.service.utils.AnalyticsUtils;
import com.portfolio.analytics.service.utils.HeatmapUtils;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.analytics.Heatmap;
import com.portfolio.model.analytics.request.AdvancedAnalyticsRequest;
import com.portfolio.model.market.MarketData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import com.portfolio.redis.service.PortfolioHeatmapRedisService;

/**
 * Provider for portfolio sector heatmap analytics
 */
@Service
@Slf4j
public class PortfolioHeatmapProvider extends AbstractPortfolioAnalyticsProvider<Heatmap> {

    @org.springframework.lang.Nullable
    private final PortfolioHeatmapRedisService heatmapRedisService;

    public PortfolioHeatmapProvider(PortfolioService portfolioService, MarketDataService marketDataService, SecurityDetailsService securityDetailsService, @org.springframework.lang.Nullable PortfolioHeatmapRedisService heatmapRedisService) {
        super(portfolioService, marketDataService, securityDetailsService);
        this.heatmapRedisService = heatmapRedisService;
    }

    @Override
    public AnalyticsType getType() {
        return AnalyticsType.SECTOR_HEATMAP;
    }

    @Override
    public Heatmap generateAnalytics(AdvancedAnalyticsRequest request) {
        log.info("Generating sector heatmap for portfolio: {}", request.getCoreIdentifiers().getPortfolioId());
        
        String portfolioId = request.getCoreIdentifiers().getPortfolioId();
        
        // Check cache first
        if (heatmapRedisService != null) {
            Optional<Heatmap> cached = heatmapRedisService.getCachedHeatmap(portfolioId, request);
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        return processPortfolioData(
            portfolioId,
            request,
            this::createEmptyResult,
            (portfolio, portfolioSymbols, marketData) -> {
                // Create a map of symbol to holding quantity
                Map<String, Double> symbolToQuantity = createSymbolToQuantityMap(portfolio);
                
                // Retrieve market cap and sector data using the new helper
                Map<String, com.am.common.amcommondata.model.security.SecurityModel> securityDetails = getSecurityDetails(portfolioSymbols, request);
                
                // Align with sector allocation: group by security metadata sectors.
                Map<String, List<String>> sectorToStocks = securityDetailsService
                    .groupSymbolsBySector(portfolioSymbols, securityDetails);

                // Equity holding sector fills gaps when security metadata is Unknown.
                if (portfolio.getEquityModels() != null) {
                    for (EquityModel model : portfolio.getEquityModels()) {
                        String symbol = model.getSymbol();
                        if (symbol == null || symbol.trim().isEmpty()) {
                            continue;
                        }
                        String equitySector = (model.getSector() != null
                                && !model.getSector().trim().isEmpty()
                                && !model.getSector().trim().equals("-"))
                            ? model.getSector().trim()
                            : null;
                        if (equitySector == null) {
                            continue;
                        }

                        String currentSector = null;
                        for (Map.Entry<String, List<String>> entry : sectorToStocks.entrySet()) {
                            if (entry.getValue().contains(symbol)) {
                                currentSector = entry.getKey();
                                break;
                            }
                        }
                        if (currentSector == null
                                || "Unknown".equalsIgnoreCase(currentSector)
                                || "-".equals(currentSector)) {
                            if (currentSector != null) {
                                sectorToStocks.get(currentSector).remove(symbol);
                                if (sectorToStocks.get(currentSector).isEmpty()) {
                                    sectorToStocks.remove(currentSector);
                                }
                            }
                            sectorToStocks.computeIfAbsent(equitySector, k -> new ArrayList<>()).add(symbol);
                        }
                    }
                }
                
                // Process market data by sector and compute total portfolio value in one pass
                Map<String, List<MarketData>> sectorMarketDataMap = new HashMap<>();
                Map<String, List<Double>> sectorQuantitiesMap = new HashMap<>();
                double[] totalPortfolioValue = {0.0};
                groupMarketDataBySector(marketData, sectorToStocks, symbolToQuantity, sectorMarketDataMap, sectorQuantitiesMap, totalPortfolioValue);
                
                // Day/% live override only for 1D / live requests. Historical TF uses
                // HeatmapUtils period return from hist previousClose → lastPrice.
                Map<String, Double> symbolToChangePercent = new HashMap<>();
                if (shouldApplyTodayChangeOverride(request) && portfolio.getEquityModels() != null) {
                    for (EquityModel model : portfolio.getEquityModels()) {
                        if (model.getSymbol() != null && model.getTodayProfitLossPercentage() != null) {
                            symbolToChangePercent.put(model.getSymbol(), model.getTodayProfitLossPercentage());
                        }
                    }
                }
                
                // Calculate performance for each sector
                List<Heatmap.SectorPerformance> sectorPerformances = calculateSectorPerformances(sectorMarketDataMap, sectorQuantitiesMap, symbolToQuantity, symbolToChangePercent, totalPortfolioValue[0]);
                
                // Create heatmap with domain-driven approach
                Heatmap heatmap = Heatmap.builder()
                    .portfolioId(portfolioId)
                    .timestamp(Instant.now())
                    .sectors(sectorPerformances)
                    .build();
                    
                // Use domain method to sort sectors
                heatmap.sortSectorsByPerformance();
                
                log.info("Generated heatmap with {} sectors for portfolio: {}", sectorPerformances.size(), portfolioId);
                
                if (heatmapRedisService != null
                        && heatmap.getSectors() != null
                        && !heatmap.getSectors().isEmpty()) {
                    heatmapRedisService.cacheHeatmap(heatmap, portfolioId, request);
                }
                
                return heatmap;
            }
        );
    }
    
    /**
     * Create empty result when no data is available
     */
    private Heatmap createEmptyResult() {
        return Heatmap.builder()
            .timestamp(Instant.now())
            .sectors(Collections.emptyList())
            .build();
    }

    /**
     * Holdings day P&amp;L override applies only for live / 1D heatmap.
     * Non-1D requests must use hist period returns from market data.
     */
    static boolean shouldApplyTodayChangeOverride(AdvancedAnalyticsRequest request) {
        if (request == null || request.getTimeFrameRequest() == null) {
            return true;
        }
        com.portfolio.model.market.TimeFrame tf = request.getTimeFrame();
        return tf == null || tf == com.portfolio.model.market.TimeFrame.DAY;
    }
    
    /**
     * Create a map of symbol to holding quantity
     */
    private Map<String, Double> createSymbolToQuantityMap(PortfolioModelV1 portfolio) {
        log.debug("Creating symbol to quantity map for portfolio: {}", portfolio.getName());
        return portfolio.getEquityModels().stream()
            .collect(Collectors.toMap(
                EquityModel::getSymbol,
                EquityModel::getQuantity,
                (a, b) -> a + b // In case of duplicate symbols, sum the quantities
            ));
    }
    
    /**
     * Group market data by sector
     */
    private void groupMarketDataBySector(
            Map<String, MarketData> marketData,
            Map<String, List<String>> sectorToStocks,
            Map<String, Double> symbolToQuantity,
            Map<String, List<MarketData>> sectorMarketDataMap,
            Map<String, List<Double>> sectorQuantitiesMap,
            double[] totalPortfolioValue) {
        log.debug("Grouping {} stocks by sector", marketData.size());
        
        // Build inverted index for O(1) sector lookup
        Map<String, String> symbolToSector = new HashMap<>();
        sectorToStocks.forEach((sector, symbols) -> 
            symbols.forEach(sym -> symbolToSector.put(sym, sector))
        );
        
        for (String symbol : symbolToQuantity.keySet()) {
            MarketData data = AnalyticsUtils.resolveMarketData(marketData, symbol);
            if (data == null) {
                log.warn("Null market data encountered for symbol: {}", symbol);
                continue;
            }

            String sector = symbolToSector.getOrDefault(symbol, "Unknown");
            
            sectorMarketDataMap.computeIfAbsent(sector, k -> new ArrayList<>())
                .add(data);
            
            double quantity = symbolToQuantity.getOrDefault(symbol, 0.0);
            sectorQuantitiesMap.computeIfAbsent(sector, k -> new ArrayList<>())
                .add(quantity);
                
            double resolvedPrice = 0.0;
            if (data.getLastPrice() != null && data.getLastPrice() > 0) {
                resolvedPrice = data.getLastPrice();
            } else if (data.getOhlc() != null && data.getOhlc().getClose() > 0) {
                resolvedPrice = data.getOhlc().getClose();
            } else if (data.getPreviousClose() != null && data.getPreviousClose() > 0) {
                resolvedPrice = data.getPreviousClose();
            }
            totalPortfolioValue[0] += resolvedPrice * quantity;
        }
    }
    
    
    
    /**
     * Calculate performance for each sector with domain-driven approach
     */
    private List<Heatmap.SectorPerformance> calculateSectorPerformances(
            Map<String, List<MarketData>> sectorMarketDataMap,
            Map<String, List<Double>> sectorQuantitiesMap,
            Map<String, Double> symbolToQuantity,
            Map<String, Double> symbolToChangePercent,
            double totalPortfolioValue) {
        
        log.debug("Calculating performance metrics for {} sectors", sectorMarketDataMap.size());
        List<Heatmap.SectorPerformance> sectorPerformances = new ArrayList<>();
        
        log.debug("Total portfolio value for weightage calculation: {}", totalPortfolioValue);
        
        for (Map.Entry<String, List<MarketData>> entry : sectorMarketDataMap.entrySet()) {
            String sectorName = entry.getKey();
            List<MarketData> sectorStocks = entry.getValue();
            List<Double> quantities = sectorQuantitiesMap.get(sectorName);
            
            // Extract symbols for this sector
            List<String> symbols = new ArrayList<>();
            for (MarketData data : sectorStocks) {
                // Get symbol from MarketData or use a placeholder if null
                String symbol = data.getSymbol();
                if (symbol == null || symbol.isEmpty()) {
                    // Try to find the symbol by looking up in the symbolToQuantity map
                    for (Map.Entry<String, Double> symbolEntry : symbolToQuantity.entrySet()) {
                        if (symbolEntry.getValue().equals(quantities.get(symbols.size()))) {
                            symbol = symbolEntry.getKey();
                            break;
                        }
                    }
                    
                    // If still null, use a placeholder
                    if (symbol == null || symbol.isEmpty()) {
                        symbol = "UNKNOWN-" + symbols.size();
                    }
                }
                symbols.add(symbol);
                log.debug("Added symbol: {} to sector: {}", symbol, sectorName);
            }
            
            // Create complete sector performance with stock details using domain-driven approach
            Heatmap.SectorPerformance sectorPerformance = HeatmapUtils.createCompleteSectorPerformance(
                sectorName,
                null, // Let the utility generate a sector code
                sectorStocks,
                quantities,
                symbols,
                totalPortfolioValue,
                symbolToChangePercent);
            
            sectorPerformances.add(sectorPerformance);
        }
        
        return sectorPerformances;
    }
    
    // calculateTotalSectorValues has been merged into groupMarketDataBySector for performance
    
    // SectorMetrics class and related methods have been moved to HeatmapUtils
}
