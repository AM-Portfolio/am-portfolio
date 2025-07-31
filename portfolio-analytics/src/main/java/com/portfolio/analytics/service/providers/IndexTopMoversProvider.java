package com.portfolio.analytics.service.providers;

import com.portfolio.analytics.service.AbstractIndexAnalyticsProvider;
import com.portfolio.analytics.service.AnalyticsType;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.marketdata.service.NseIndicesService;
import com.portfolio.model.analytics.GainerLoser;
import com.portfolio.model.market.MarketData;
import com.am.common.amcommondata.model.security.SecurityModel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Provider for top movers (gainers and losers) analytics
 */
@Service
@Slf4j
public class IndexTopMoversProvider extends AbstractIndexAnalyticsProvider<GainerLoser> {

    public IndexTopMoversProvider(NseIndicesService nseIndicesService, MarketDataService marketDataService, SecurityDetailsService securityDetailsService) {
        super(nseIndicesService, marketDataService, securityDetailsService);
    }

    @Override
    public AnalyticsType getType() {
        return AnalyticsType.TOP_MOVERS;
    }

    @Override
    public GainerLoser generateAnalytics(String indexSymbol) {
        // Default to 5 top movers
        return generateAnalytics(indexSymbol, 5);
    }

    // Default limit for top movers
    private static final int DEFAULT_LIMIT = 5;
    
    @Override
    public GainerLoser generateAnalytics(String indexSymbol, Object... params) {
        // Extract limit parameter if provided
        int limit = extractLimit(params);
        
        log.info("Getting top {} gainers and losers for index: {}", limit, indexSymbol);
        
        // Get index symbols and market data
        List<String> indexStockSymbols = getIndexSymbols(indexSymbol);
        if (indexStockSymbols.isEmpty()) {
            return createEmptyResult(indexSymbol);
        }
        
        Map<String, MarketData> marketData = getMarketData(indexStockSymbols);
        if (marketData.isEmpty()) {
            return createEmptyResult(indexSymbol);
        }
        
        // Calculate performance metrics
        Map<String, Double> symbolToPerformance = new HashMap<>();
        Map<String, Double> symbolToChangePercent = new HashMap<>();
        calculatePerformanceMetrics(marketData, symbolToPerformance, symbolToChangePercent);
        
        // Get top gainers and losers
        List<GainerLoser.StockMovement> gainers = getTopGainers(marketData, symbolToPerformance, symbolToChangePercent, limit);
        List<GainerLoser.StockMovement> losers = getTopLosers(marketData, symbolToPerformance, symbolToChangePercent, limit);
        
        // Calculate sector movements
        List<GainerLoser.SectorMovement> sectorMovements = calculateSectorMovements(indexStockSymbols, marketData, symbolToPerformance, symbolToChangePercent);
        
        return GainerLoser.builder()
            .indexSymbol(indexSymbol)
            .timestamp(Instant.now())
            .topGainers(gainers)
            .topLosers(losers)
            .sectorMovements(sectorMovements)
            .build();
    }
    
    /**
     * Extract limit parameter from varargs
     */
    private int extractLimit(Object... params) {
        if (params.length > 0 && params[0] instanceof Integer) {
            return (Integer) params[0];
        }
        return DEFAULT_LIMIT;
    }
    
    /**
     * Create empty result when no data is available
     */
    private GainerLoser createEmptyResult(String indexSymbol) {
        return GainerLoser.builder()
            .indexSymbol(indexSymbol)
            .timestamp(Instant.now())
            .topGainers(Collections.emptyList())
            .topLosers(Collections.emptyList())
            .build();
    }
    
    /**
     * Calculate performance metrics for each stock
     */
    private void calculatePerformanceMetrics(
            Map<String, MarketData> marketData, 
            Map<String, Double> symbolToPerformance, 
            Map<String, Double> symbolToChangePercent) {
        log.debug("Calculating performance metrics for {} symbols", marketData.size());
        
        int processedCount = 0;
        int validDataCount = 0;
        for (Map.Entry<String, MarketData> entry : marketData.entrySet()) {
            String symbol = entry.getKey();
            MarketData data = entry.getValue();
            
            double closePrice = data.getOhlc().getClose();
            double openPrice = data.getOhlc().getOpen();
            double lastPrice = data.getLastPrice();
            
            if (closePrice <= 0) {
                log.warn("Invalid close price ({}<=0) for symbol: {}", closePrice, symbol);
                continue;
            }
            validDataCount++;
            
            // Calculate intraday change percentage
            double changePercent = ((lastPrice - openPrice) / openPrice) * 100;
            symbolToChangePercent.put(symbol, changePercent);
            
            // Performance score based on price movement relative to previous close
            double performance = (lastPrice - closePrice) / closePrice;
            double changePercentPerformance = performance * 100;
            
            symbolToPerformance.put(symbol, performance);
            symbolToChangePercent.put(symbol, changePercentPerformance);
            
            processedCount++;
            
            log.trace("Symbol: {}, Close: {}, Last: {}, Performance: {}, Change%: {}%", 
                    symbol, closePrice, lastPrice, String.format("%.4f", performance), String.format("%.2f", changePercentPerformance));
        }
        
        log.debug("Processed {} symbols, valid data for {} symbols", processedCount, validDataCount);
    }
    
    /**
     * Get top gainers based on performance metrics
     */
    private List<GainerLoser.StockMovement> getTopGainers(
            Map<String, MarketData> marketData,
            Map<String, Double> symbolToPerformance, 
            Map<String, Double> symbolToChangePercent, 
            int limit) {
        log.debug("Finding top {} gainers from {} symbols", limit, symbolToPerformance.size());
        
        // Get top gainers (highest positive performance)
        List<String> topGainerSymbols = symbolToPerformance.entrySet().stream()
            .filter(entry -> entry.getValue() > 0) // Only positive performers
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(limit)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
            
        log.debug("Found {} top gainers: {}", topGainerSymbols.size(), topGainerSymbols);
        
        // Create stock movement objects
        return createStockMovements(topGainerSymbols, marketData, symbolToChangePercent);
    }
    
    /**
     * Get top losers based on performance metrics
     */
    private List<GainerLoser.StockMovement> getTopLosers(
            Map<String, MarketData> marketData,
            Map<String, Double> symbolToPerformance, 
            Map<String, Double> symbolToChangePercent, 
            int limit) {
        log.debug("Finding top {} losers from {} symbols", limit, symbolToPerformance.size());
        
        // Sort stocks by performance (ascending)
        List<String> worstPerformers = symbolToPerformance.entrySet().stream()
            .filter(entry -> entry.getValue() < 0) // Only negative performers
            .sorted(Map.Entry.comparingByValue()) // Ascending order for worst performers
            .limit(limit)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        // Create stock movement objects
        return createStockMovements(worstPerformers, marketData, symbolToChangePercent);
    }
    
    /**
     * Create stock movement objects for a list of symbols
     */
    private List<GainerLoser.StockMovement> createStockMovements(
            List<String> symbols, 
            Map<String, MarketData> marketData, 
            Map<String, Double> symbolToChangePercent) {
        log.debug("Creating stock movement objects for {} symbols", symbols.size());
        
        // Get security details for all symbols
        Map<String, SecurityModel> securityDetails = securityDetailsService.getSecurityDetails(symbols);
        
        return symbols.stream()
            .map(symbol -> {
                // Get sector from security details
                String sector = "Unknown";
                String companyName = symbol;
                
                SecurityModel securityModel = securityDetails.get(symbol);
                if (securityModel != null && securityModel.getMetadata() != null) {
                    sector = securityModel.getMetadata().getSector();
                    companyName = symbol; // Use symbol as company name if not available
                    
                    if (sector == null) {
                        sector = "Unknown";
                    }
                }
                
                MarketData data = marketData.get(symbol);
                if (data.getOhlc() == null) {
                    log.warn("Missing OHLC data for symbol: {}", symbol);
                    return null;
                }
                
                // Calculate change amount with precision
                double lastPrice = data.getLastPrice();
                double closePrice = data.getOhlc().getClose();
                Double changeAmount = Math.round((lastPrice - closePrice) * 100.0) / 100.0;
                Double changePercent = Math.round(symbolToChangePercent.getOrDefault(symbol, 0.0) * 100.0) / 100.0;
                
                return GainerLoser.StockMovement.builder()
                    .symbol(symbol)
                    .companyName(companyName)
                    .changePercent(changePercent)
                    .ohlcData(marketData.get(symbol).getOhlc())
                    .lastPrice(lastPrice)
                    .changeAmount(changeAmount)
                    .sector(sector)
                    .build();
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Calculate sector-wise movement data
     */
    private List<GainerLoser.SectorMovement> calculateSectorMovements(
            List<String> symbols,
            Map<String, MarketData> marketData,
            Map<String, Double> symbolToPerformance,
            Map<String, Double> symbolToChangePercent) {
        log.debug("Calculating sector movements for {} symbols", symbols.size());
        
        // Group symbols by sector
        Map<String, List<String>> sectorToSymbols = securityDetailsService.groupSymbolsBySector(symbols);
        log.debug("Found {} sectors: {}", sectorToSymbols.size(), sectorToSymbols.keySet());
        
        // Calculate sector movements
        List<GainerLoser.SectorMovement> sectorMovements = sectorToSymbols.entrySet().stream()
            .map(entry -> {
                String sectorName = entry.getKey();
                List<String> sectorSymbols = entry.getValue();
                
                // Calculate average change percent for the sector
                double avgChangePercent = sectorSymbols.stream()
                    .filter(symbol -> symbolToChangePercent.containsKey(symbol))
                    .mapToDouble(symbol -> symbolToChangePercent.getOrDefault(symbol, 0.0))
                    .average()
                    .orElse(0.0);
                
                // Round to 2 decimal places
                avgChangePercent = roundToTwoDecimals(avgChangePercent);
                
                // Get top gainers in this sector
                List<String> topGainerSymbols = sectorSymbols.stream()
                    .filter(symbol -> symbolToPerformance.containsKey(symbol) && symbolToPerformance.get(symbol) > 0)
                    .sorted(Comparator.comparing(symbol -> -symbolToPerformance.getOrDefault(symbol, 0.0)))
                    .limit(3) // Top 3 gainers per sector
                    .collect(Collectors.toList());
                
                // Get top losers in this sector
                List<String> topLoserSymbols = sectorSymbols.stream()
                    .filter(symbol -> symbolToPerformance.containsKey(symbol) && symbolToPerformance.get(symbol) < 0)
                    .sorted(Comparator.comparing(symbol -> symbolToPerformance.getOrDefault(symbol, 0.0)))
                    .limit(3) // Top 3 losers per sector
                    .collect(Collectors.toList());
                
                // Create stock performance map with values rounded to 2 decimal places
                Map<String, Double> stockPerformance = sectorSymbols.stream()
                    .filter(symbol -> symbolToPerformance.containsKey(symbol))
                    .collect(Collectors.toMap(
                        Function.identity(),
                        symbol -> roundToTwoDecimals(symbolToPerformance.getOrDefault(symbol, 0.0))
                    ));
                
                // Build sector movement object
                return GainerLoser.SectorMovement.builder()
                    .sectorName(sectorName)
                    .averageChangePercent(avgChangePercent)
                    .stockCount(sectorSymbols.size())
                    .topGainerSymbols(topGainerSymbols)
                    .topLoserSymbols(topLoserSymbols)
                    .stockPerformance(stockPerformance)
                    .build();
            })
            .sorted(Comparator.comparing(GainerLoser.SectorMovement::getAverageChangePercent).reversed())
                .collect(Collectors.toList());
        
        log.info("Generated sector movements for {} sectors", sectorMovements.size());  
        return sectorMovements;
    }
    
    /**
     * Helper method to round a double value to 2 decimal places
     */
    private double roundToTwoDecimals(double value) {
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
