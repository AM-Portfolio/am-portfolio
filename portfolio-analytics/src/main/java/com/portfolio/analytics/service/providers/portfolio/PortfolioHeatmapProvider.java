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
import com.portfolio.model.portfolio.HoldingInstrumentClassifier;
import com.portfolio.model.resolver.TradingSymbolResolver;
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
                // ISIN-as-symbol (INF… ETF / unlisted) → broker display ticker for mosaic labels.
                applyDisplayTickers(portfolio);
                List<String> displaySymbols = getPortfolioSymbols(portfolio);
                Map<String, MarketData> displayMarketData = remappedMarketData(marketData, portfolio);

                // Create a map of symbol to holding quantity
                Map<String, Double> symbolToQuantity = createSymbolToQuantityMap(portfolio);
                
                // Retrieve market cap and sector data using the new helper
                Map<String, com.am.common.amcommondata.model.security.SecurityModel> securityDetails =
                        getSecurityDetails(displaySymbols, request);
                
                // Align with sector allocation: group by security metadata sectors.
                Map<String, List<String>> sectorToStocks = securityDetailsService
                    .groupSymbolsBySector(displaySymbols, securityDetails);

                Map<String, EquityModel> equityBySymbol = indexEquities(portfolio);
                // Equity holding sector + instrument classifier fills Unknown / blank meta.
                if (portfolio.getEquityModels() != null) {
                    for (EquityModel model : portfolio.getEquityModels()) {
                        String symbol = model.getSymbol();
                        if (symbol == null || symbol.trim().isEmpty()) {
                            continue;
                        }
                        String equitySector = usableEquitySector(model.getSector());
                        if (equitySector == null) {
                            boolean hasMd = AnalyticsUtils.resolveMarketData(displayMarketData, symbol) != null;
                            String priceSourceHint = hasMd
                                    ? HoldingInstrumentClassifier.PRICE_LIVE
                                    : HoldingInstrumentClassifier.PRICE_COST;
                            equitySector = HoldingInstrumentClassifier.resolveSector(
                                    null, symbol, model.getIsin(), priceSourceHint);
                        }
                        if (equitySector == null || HoldingInstrumentClassifier.isMissingSector(equitySector)) {
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
                                || HoldingInstrumentClassifier.isMissingSector(currentSector)) {
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
                Map<String, String> symbolPriceSource = new HashMap<>();
                double[] totalPortfolioValue = {0.0};
                groupMarketDataBySector(
                        displayMarketData,
                        sectorToStocks,
                        symbolToQuantity,
                        equityBySymbol,
                        sectorMarketDataMap,
                        sectorQuantitiesMap,
                        symbolPriceSource,
                        totalPortfolioValue);
                
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
                List<Heatmap.SectorPerformance> sectorPerformances = calculateSectorPerformances(
                        sectorMarketDataMap,
                        sectorQuantitiesMap,
                        symbolToQuantity,
                        symbolToChangePercent,
                        symbolPriceSource,
                        totalPortfolioValue[0]);
                
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
     * Group market data by sector. Cost-basis synthetic quotes keep unlisted rows on the mosaic.
     */
    private void groupMarketDataBySector(
            Map<String, MarketData> marketData,
            Map<String, List<String>> sectorToStocks,
            Map<String, Double> symbolToQuantity,
            Map<String, EquityModel> equityBySymbol,
            Map<String, List<MarketData>> sectorMarketDataMap,
            Map<String, List<Double>> sectorQuantitiesMap,
            Map<String, String> symbolPriceSource,
            double[] totalPortfolioValue) {
        log.debug("Grouping {} stocks by sector", marketData != null ? marketData.size() : 0);
        
        // Build inverted index for O(1) sector lookup
        Map<String, String> symbolToSector = new HashMap<>();
        sectorToStocks.forEach((sector, symbols) -> 
            symbols.forEach(sym -> symbolToSector.put(sym, sector))
        );
        
        for (String symbol : symbolToQuantity.keySet()) {
            MarketData data = AnalyticsUtils.resolveMarketData(marketData, symbol);
            String priceSource = HoldingInstrumentClassifier.PRICE_LIVE;
            if (data == null || !hasResolvablePrice(data)) {
                EquityModel equity = equityBySymbol.get(symbol);
                Double cost = costBasisPrice(equity);
                if (cost == null || cost <= 0) {
                    log.warn("Null market data and no cost basis for symbol: {}", symbol);
                    continue;
                }
                data = MarketData.builder()
                        .symbol(symbol)
                        .lastPrice(cost)
                        .previousClose(cost)
                        .build();
                priceSource = HoldingInstrumentClassifier.PRICE_COST;
                log.info("Heatmap cost-basis fallback for symbol={} price={}", symbol, cost);
            }

            String sector = symbolToSector.getOrDefault(symbol, "Unknown");
            if (HoldingInstrumentClassifier.isMissingSector(sector)) {
                EquityModel equity = equityBySymbol.get(symbol);
                String classified = HoldingInstrumentClassifier.resolveSector(
                        null,
                        symbol,
                        equity != null ? equity.getIsin() : null,
                        priceSource);
                if (classified != null && !HoldingInstrumentClassifier.isMissingSector(classified)) {
                    sector = classified;
                }
            }
            
            sectorMarketDataMap.computeIfAbsent(sector, k -> new ArrayList<>())
                .add(data);
            
            double quantity = symbolToQuantity.getOrDefault(symbol, 0.0);
            sectorQuantitiesMap.computeIfAbsent(sector, k -> new ArrayList<>())
                .add(quantity);
            symbolPriceSource.put(symbol, priceSource);
                
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

    private static Map<String, EquityModel> indexEquities(PortfolioModelV1 portfolio) {
        Map<String, EquityModel> map = new HashMap<>();
        if (portfolio == null || portfolio.getEquityModels() == null) {
            return map;
        }
        for (EquityModel e : portfolio.getEquityModels()) {
            if (e != null && e.getSymbol() != null && !e.getSymbol().isBlank()) {
                map.put(e.getSymbol(), e);
            }
        }
        return map;
    }

    /**
     * Replace ISIN-shaped equity symbols with broker display tickers (request-scoped; not persisted).
     */
    static void applyDisplayTickers(PortfolioModelV1 portfolio) {
        if (portfolio == null || portfolio.getEquityModels() == null) {
            return;
        }
        for (EquityModel equity : portfolio.getEquityModels()) {
            if (equity == null) {
                continue;
            }
            String symbol = equity.getSymbol();
            if (!TradingSymbolResolver.looksLikeIsin(symbol)) {
                continue;
            }
            String suggested = HoldingInstrumentClassifier.suggestDisplayTickerFromName(equity.getName());
            if (suggested == null || suggested.isBlank()) {
                continue;
            }
            if (HoldingInstrumentClassifier.isUnsafeNameToTickerMap(equity.getName(), suggested)) {
                continue;
            }
            if (equity.getIsin() == null || equity.getIsin().isBlank()) {
                equity.setIsin(symbol.trim().toUpperCase(Locale.ROOT));
            }
            log.info("Heatmap display ticker {} → {} (isin={})", symbol, suggested, equity.getIsin());
            equity.setSymbol(suggested);
        }
    }

    /**
     * Re-key market data from ISIN keys to display tickers after {@link #applyDisplayTickers}.
     */
    static Map<String, MarketData> remappedMarketData(
            Map<String, MarketData> marketData, PortfolioModelV1 portfolio) {
        if (marketData == null || marketData.isEmpty() || portfolio == null
                || portfolio.getEquityModels() == null) {
            return marketData != null ? marketData : Map.of();
        }
        Map<String, MarketData> out = new HashMap<>(marketData);
        for (EquityModel equity : portfolio.getEquityModels()) {
            if (equity == null || equity.getSymbol() == null) {
                continue;
            }
            String display = equity.getSymbol();
            if (out.containsKey(display)) {
                continue;
            }
            String isin = equity.getIsin();
            MarketData byIsin = isin != null ? AnalyticsUtils.resolveMarketData(marketData, isin) : null;
            if (byIsin != null) {
                out.put(display, MarketData.builder()
                        .symbol(display)
                        .lastPrice(byIsin.getLastPrice())
                        .previousClose(byIsin.getPreviousClose())
                        .ohlc(byIsin.getOhlc())
                        .build());
            }
        }
        return out;
    }

    private static String usableEquitySector(String sector) {
        if (HoldingInstrumentClassifier.isMissingSector(sector)) {
            return null;
        }
        return sector.trim();
    }

    private static boolean hasResolvablePrice(MarketData data) {
        if (data == null) {
            return false;
        }
        if (data.getLastPrice() != null && data.getLastPrice() > 0) {
            return true;
        }
        if (data.getOhlc() != null && data.getOhlc().getClose() > 0) {
            return true;
        }
        return data.getPreviousClose() != null && data.getPreviousClose() > 0;
    }

    private static Double costBasisPrice(EquityModel equity) {
        if (equity == null) {
            return null;
        }
        if (equity.getAvgBuyingPrice() != null && equity.getAvgBuyingPrice() > 0) {
            return equity.getAvgBuyingPrice();
        }
        if (equity.getInvestmentValue() != null
                && equity.getQuantity() != null
                && equity.getQuantity() > 0) {
            return equity.getInvestmentValue() / equity.getQuantity();
        }
        return null;
    }
    
    
    
    /**
     * Calculate performance for each sector with domain-driven approach
     */
    private List<Heatmap.SectorPerformance> calculateSectorPerformances(
            Map<String, List<MarketData>> sectorMarketDataMap,
            Map<String, List<Double>> sectorQuantitiesMap,
            Map<String, Double> symbolToQuantity,
            Map<String, Double> symbolToChangePercent,
            Map<String, String> symbolPriceSource,
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

            if (sectorPerformance.getStocks() != null && symbolPriceSource != null) {
                for (Heatmap.StockDetail stock : sectorPerformance.getStocks()) {
                    if (stock != null && stock.getSymbol() != null) {
                        stock.setPriceSource(symbolPriceSource.get(stock.getSymbol()));
                    }
                }
            }
            
            sectorPerformances.add(sectorPerformance);
        }
        
        return sectorPerformances;
    }
    
    // calculateTotalSectorValues has been merged into groupMarketDataBySector for performance
    
    // SectorMetrics class and related methods have been moved to HeatmapUtils
}
