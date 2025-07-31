package com.portfolio.analytics.service.utils;

import com.portfolio.model.analytics.GainerLoser;
import com.portfolio.model.market.MarketData;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility class for top movers (gainers and losers) operations shared between
 * IndexTopMoversProvider and PortfolioTopMoversProvider
 */
@Slf4j
public class TopMoverUtils {

    // Default limit for top movers
    public static final int DEFAULT_LIMIT = 5;

    /**
     * Calculate performance metrics for each stock
     * @param marketData Map of market data by symbol
     * @param symbolToPerformance Map to store performance metrics
     * @param symbolToChangePercent Map to store change percentages
     */
    public static void calculatePerformanceMetrics(
            Map<String, MarketData> marketData,
            Map<String, Double> symbolToPerformance,
            Map<String, Double> symbolToChangePercent) {
        
        log.debug("Calculating performance metrics for {} stocks", marketData.size());
        
        marketData.forEach((symbol, data) -> {
            if (data.getOhlc() != null) {
                double closePrice = data.getOhlc().getClose();
                double openPrice = data.getOhlc().getOpen();
                
                if (openPrice > 0 && closePrice > 0) {
                    // Calculate change from open to current price
                    double changePercent = ((data.getLastPrice() - openPrice) / openPrice) * 100;
                    symbolToChangePercent.put(symbol, changePercent);
                    
                    // Performance score based on price movement relative to previous close
                    double performanceScore = ((data.getLastPrice() - closePrice) / closePrice) * 100;
                    symbolToPerformance.put(symbol, performanceScore);
                    
                    log.trace("Symbol: {}, Performance: {}, Change: {}%", 
                            symbol, performanceScore, changePercent);
                }
            }
        });
        
        log.debug("Calculated performance metrics for {} stocks", symbolToPerformance.size());
    }

    /**
     * Get top gainers based on performance metrics
     * @param marketData Map of market data by symbol
     * @param symbolToPerformance Map of performance metrics by symbol
     * @param symbolToChangePercent Map of change percentages by symbol
     * @param limit Number of top gainers to return
     * @return List of top gainer stock movements
     */
    public static List<GainerLoser.StockMovement> getTopGainers(
            Map<String, MarketData> marketData,
            Map<String, Double> symbolToPerformance,
            Map<String, Double> symbolToChangePercent,
            int limit) {
        
        log.debug("Finding top {} gainers", limit);
        
        // Get symbols with positive performance, sorted by performance (descending)
        List<String> topGainerSymbols = symbolToPerformance.entrySet().stream()
                .filter(entry -> entry.getValue() > 0) // Only positive performers
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()) // Sort by performance (descending)
                .limit(limit) // Take top N
                .map(Map.Entry::getKey) // Get symbols
                .collect(Collectors.toList());
        
        log.debug("Found {} gainers", topGainerSymbols.size());
        
        // Create stock movement objects for top gainers
        return createStockMovements(topGainerSymbols, marketData, symbolToChangePercent);
    }

    /**
     * Get top losers based on performance metrics
     * @param marketData Map of market data by symbol
     * @param symbolToPerformance Map of performance metrics by symbol
     * @param symbolToChangePercent Map of change percentages by symbol
     * @param limit Number of top losers to return
     * @return List of top loser stock movements
     */
    public static List<GainerLoser.StockMovement> getTopLosers(
            Map<String, MarketData> marketData,
            Map<String, Double> symbolToPerformance,
            Map<String, Double> symbolToChangePercent,
            int limit) {
        
        log.debug("Finding top {} losers", limit);
        
        // Get symbols with negative performance, sorted by performance (ascending)
        List<String> topLoserSymbols = symbolToPerformance.entrySet().stream()
                .filter(entry -> entry.getValue() < 0) // Only negative performers
                .sorted(Map.Entry.comparingByValue()) // Sort by performance (ascending)
                .limit(limit) // Take top N
                .map(Map.Entry::getKey) // Get symbols
                .collect(Collectors.toList());
        
        log.debug("Found {} losers", topLoserSymbols.size());
        
        // Create stock movement objects for top losers
        return createStockMovements(topLoserSymbols, marketData, symbolToChangePercent);
    }

    /**
     * Create stock movement objects for a list of symbols
     * @param symbols List of symbols
     * @param marketData Map of market data by symbol
     * @param symbolToChangePercent Map of change percentages by symbol
     * @return List of stock movement objects
     */
    public static List<GainerLoser.StockMovement> createStockMovements(
            List<String> symbols,
            Map<String, MarketData> marketData,
            Map<String, Double> symbolToChangePercent) {
        
        return symbols.stream()
                .<GainerLoser.StockMovement>map(symbol -> {
                    MarketData data = marketData.get(symbol);
                    if (data == null) {
                        return null;
                    }
                    
                    double lastPrice = data.getLastPrice();
                    double openPrice = data.getOhlc().getOpen();
                    double changeAmount = lastPrice - openPrice;
                    double changePercent = symbolToChangePercent.getOrDefault(symbol, 0.0);
                    
                    // Round values to 2 decimal places
                    lastPrice = roundToTwoDecimals(lastPrice);
                    changeAmount = roundToTwoDecimals(changeAmount);
                    changePercent = roundToTwoDecimals(changePercent);
                    
                    // Get security details for company name and sector
                    String companyName = symbol; // Default to symbol if company name not available
                    String sector = "Unknown"; // Default sector
                    
                    return GainerLoser.StockMovement.builder()
                            .symbol(symbol)
                            .companyName(companyName)
                            .lastPrice(lastPrice)
                            .ohlcData(data.getOhlc())
                            .changeAmount(changeAmount)
                            .changePercent(changePercent)
                            .sector(sector)
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Calculate sector-wise movement data
     * @param symbols List of symbols
     * @param marketData Map of market data by symbol
     * @param symbolToPerformance Map of performance metrics by symbol
     * @param symbolToChangePercent Map of change percentages by symbol
     * @param securityDetailsService Service to get security details
     * @return List of sector movement objects
     */
    public static List<GainerLoser.SectorMovement> calculateSectorMovements(
            List<String> symbols,
            Map<String, MarketData> marketData,
            Map<String, Double> symbolToPerformance,
            Map<String, Double> symbolToChangePercent,
            SecurityDetailsService securityDetailsService) {
        
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
     * Calculate weighted sector movements for portfolio
     * @param symbols List of symbols
     * @param marketData Map of market data by symbol
     * @param symbolToPerformance Map of performance metrics by symbol
     * @param symbolToChangePercent Map of change percentages by symbol
     * @param symbolToQuantity Map of quantities by symbol
     * @param securityDetailsService Service to get security details
     * @return List of sector movement objects
     */
    public static List<GainerLoser.SectorMovement> calculateWeightedSectorMovements(
            List<String> symbols,
            Map<String, MarketData> marketData,
            Map<String, Double> symbolToPerformance,
            Map<String, Double> symbolToChangePercent,
            Map<String, Double> symbolToQuantity,
            SecurityDetailsService securityDetailsService) {
        
        log.debug("Calculating weighted sector movements for {} symbols", symbols.size());
        
        // Group symbols by sector
        Map<String, List<String>> sectorToSymbols = securityDetailsService.groupSymbolsBySector(symbols);
        
        // Calculate total portfolio value
        double totalPortfolioValue = symbols.stream()
                .filter(symbol -> marketData.containsKey(symbol) && symbolToQuantity.containsKey(symbol))
                .mapToDouble(symbol -> {
                    MarketData data = marketData.get(symbol);
                    double quantity = symbolToQuantity.getOrDefault(symbol, 0.0);
                    return data.getLastPrice() * quantity;
                })
                .sum();
        
        // Calculate sector movements with weights
        List<GainerLoser.SectorMovement> sectorMovements = sectorToSymbols.entrySet().stream()
                .map(entry -> {
                    String sectorName = entry.getKey();
                    List<String> sectorSymbols = entry.getValue();
                    
                    // Calculate sector value and weighted metrics
                    double sectorValue = 0.0;
                    double weightedChangeSum = 0.0;
                    
                    for (String symbol : sectorSymbols) {
                        if (marketData.containsKey(symbol) && symbolToQuantity.containsKey(symbol)) {
                            MarketData data = marketData.get(symbol);
                            double quantity = symbolToQuantity.get(symbol);
                            double value = data.getLastPrice() * quantity;
                            sectorValue += value;
                            
                            if (symbolToChangePercent.containsKey(symbol)) {
                                weightedChangeSum += symbolToChangePercent.get(symbol) * value;
                            }
                        }
                    }
                    
                    // Calculate weighted average change percent
                    double avgChangePercent = sectorValue > 0 ? weightedChangeSum / sectorValue : 0.0;
                    avgChangePercent = roundToTwoDecimals(avgChangePercent);
                    
                    // Calculate market cap weight
                    double marketCapWeight = totalPortfolioValue > 0 ? (sectorValue / totalPortfolioValue) * 100 : 0.0;
                    marketCapWeight = roundToTwoDecimals(marketCapWeight);
                    
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
                            .marketCapWeight(marketCapWeight)
                            .topGainerSymbols(topGainerSymbols)
                            .topLoserSymbols(topLoserSymbols)
                            .stockPerformance(stockPerformance)
                            .build();
                })
                .sorted(Comparator.comparing(GainerLoser.SectorMovement::getAverageChangePercent).reversed())
                .collect(Collectors.toList());
        
        log.info("Generated weighted sector movements for {} sectors", sectorMovements.size());
        return sectorMovements;
    }

    /**
     * Helper method to round a double value to 2 decimal places
     * @param value The value to round
     * @return The rounded value
     */
    public static double roundToTwoDecimals(double value) {
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
