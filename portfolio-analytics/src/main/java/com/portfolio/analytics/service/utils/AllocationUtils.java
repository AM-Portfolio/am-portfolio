package com.portfolio.analytics.service.utils;

import com.portfolio.model.analytics.SectorAllocation;
import com.portfolio.model.market.MarketData;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for allocation operations shared between
 * IndexAllocationProvider and PortfolioAllocationProvider
 */
@Slf4j
public class AllocationUtils {

    /**
     * Calculate market caps for stocks
     * @param marketData Map of market data by symbol
     * @return Map of stock symbols to their market caps
     */
    public static Map<String, Double> calculateMarketCaps(Map<String, MarketData> marketData) {
        log.debug("Calculating market caps for {} stocks", marketData.size());
        
        Map<String, Double> stockMarketCaps = new HashMap<>();
        
        marketData.forEach((symbol, data) -> {
            if (data != null) {
                double lastPrice = data.getLastPrice();
                // Note: In a real implementation, we would get outstanding shares from a proper source
                // For now, we'll use a placeholder value or estimate based on available data
                double estimatedShares = 1000000; // Placeholder value
                
                if (lastPrice > 0) {
                    double marketCap = lastPrice * estimatedShares;
                    stockMarketCaps.put(symbol, roundToTwoDecimals(marketCap));
                    log.trace("Symbol: {}, Market Cap: {}", symbol, marketCap);
                }
            }
        });
        
        log.debug("Calculated market caps for {} stocks", stockMarketCaps.size());
        return stockMarketCaps;
    }
    
    /**
     * Calculate market values for stocks based on quantity
     * @param marketData Map of market data by symbol
     * @param symbolToQuantity Map of symbol to quantity
     * @return Map of stock symbols to their market values and total portfolio value
     */
    public static Map<String, Double> calculateMarketValues(
            Map<String, MarketData> marketData, 
            Map<String, Double> symbolToQuantity) {
        
        log.debug("Calculating market values for {} stocks", symbolToQuantity.size());
        
        Map<String, Double> stockToMarketValue = new HashMap<>();
        
        symbolToQuantity.forEach((symbol, quantity) -> {
            MarketData data = marketData.get(symbol);
            if (data != null) {
                double lastPrice = data.getLastPrice();
                double marketValue = lastPrice * quantity;
                stockToMarketValue.put(symbol, roundToTwoDecimals(marketValue));
                log.trace("Symbol: {}, Quantity: {}, Market Value: {}", symbol, quantity, marketValue);
            }
        });
        
        log.debug("Calculated market values for {} stocks", stockToMarketValue.size());
        return stockToMarketValue;
    }
    
    /**
     * Calculate total portfolio value
     * @param stockToMarketValue Map of stock symbols to their market values
     * @return Total portfolio value
     */
    public static double calculateTotalValue(Map<String, Double> stockToMarketValue) {
        return roundToTwoDecimals(stockToMarketValue.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum());
    }
    
    /**
     * Calculate sector weights
     * @param sectorToStocks Map of sector names to list of stock symbols
     * @param stockToValue Map of stock symbols to their market values or market caps
     * @param totalValue Total portfolio value or total market cap
     * @return List of sector weight objects
     */
    public static List<SectorAllocation.SectorWeight> calculateSectorWeights(
            Map<String, List<String>> sectorToStocks, 
            Map<String, Double> stockToValue, 
            double totalValue) {
        
        log.debug("Calculating sector weights for {} sectors", sectorToStocks.size());
        
        List<SectorAllocation.SectorWeight> sectorWeights = new ArrayList<>();
        
        for (Map.Entry<String, List<String>> entry : sectorToStocks.entrySet()) {
            String sectorName = entry.getKey();
            List<String> stocks = entry.getValue();
            
            // Calculate total value for this sector
            double sectorValue = calculateGroupValue(stocks, stockToValue);
            
            // Calculate weight percentage
            double weightPercentage = calculateWeightPercentage(sectorValue, totalValue);
            
            // Get top stocks by value
            List<String> topStocks = getTopStocksByValue(stocks, stockToValue, 5);
            
            sectorWeights.add(SectorAllocation.SectorWeight.builder()
                .sectorName(sectorName)
                .weightPercentage(weightPercentage)
                .marketCap(sectorValue)
                .topStocks(topStocks)
                .build());
        }
        
        // Sort by weight percentage (highest to lowest)
        sectorWeights.sort(Comparator.comparing(SectorAllocation.SectorWeight::getWeightPercentage).reversed());
        
        log.debug("Generated sector weights with {} sectors", sectorWeights.size());
        return sectorWeights;
    }
    
    /**
     * Calculate industry weights
     * @param industryToStocks Map of industry names to list of stock symbols
     * @param industryToSector Map of industry names to their parent sector
     * @param stockToValue Map of stock symbols to their market values or market caps
     * @param totalValue Total portfolio value or total market cap
     * @return List of industry weight objects
     */
    public static List<SectorAllocation.IndustryWeight> calculateIndustryWeights(
            Map<String, List<String>> industryToStocks, 
            Map<String, String> industryToSector, 
            Map<String, Double> stockToValue, 
            double totalValue) {
        
        log.debug("Calculating industry weights for {} industries", industryToStocks.size());
        
        List<SectorAllocation.IndustryWeight> industryWeights = new ArrayList<>();
        
        for (Map.Entry<String, List<String>> entry : industryToStocks.entrySet()) {
            String industryName = entry.getKey();
            List<String> stocks = entry.getValue();
            String parentSector = industryToSector.getOrDefault(industryName, "Other");
            
            // Calculate total value for this industry
            double industryValue = calculateGroupValue(stocks, stockToValue);
            
            // Calculate weight percentage
            double weightPercentage = calculateWeightPercentage(industryValue, totalValue);
            
            // Get top stocks by value
            List<String> topStocks = getTopStocksByValue(stocks, stockToValue, 3);
            
            industryWeights.add(SectorAllocation.IndustryWeight.builder()
                .industryName(industryName)
                .parentSector(parentSector)
                .weightPercentage(weightPercentage)
                .marketCap(industryValue)
                .topStocks(topStocks)
                .build());
        }
        
        // Sort by weight percentage (highest to lowest)
        industryWeights.sort(Comparator.comparing(SectorAllocation.IndustryWeight::getWeightPercentage).reversed());
        
        log.debug("Generated industry weights with {} industries", industryWeights.size());
        return industryWeights;
    }
    
    /**
     * Calculate total value for a group of stocks
     * @param stocks List of stock symbols
     * @param stockToValue Map of stock symbols to their market values or market caps
     * @return Total value for the group of stocks
     */
    public static double calculateGroupValue(List<String> stocks, Map<String, Double> stockToValue) {
        return roundToTwoDecimals(stocks.stream()
            .mapToDouble(symbol -> stockToValue.getOrDefault(symbol, 0.0))
            .sum());
    }
    
    /**
     * Calculate weight percentage
     * @param groupValue Value of the group (sector or industry)
     * @param totalValue Total value (portfolio or index)
     * @return Weight percentage
     */
    public static double calculateWeightPercentage(double groupValue, double totalValue) {
        return roundToTwoDecimals(totalValue > 0 ? (groupValue / totalValue) * 100 : 0);
    }
    
    /**
     * Get top stocks by value
     * @param stocks List of stock symbols
     * @param stockToValue Map of stock symbols to their market values or market caps
     * @param limit Number of top stocks to return
     * @return List of top stock symbols
     */
    public static List<String> getTopStocksByValue(List<String> stocks, Map<String, Double> stockToValue, int limit) {
        return stocks.stream()
            .sorted(Comparator.comparing(symbol -> -stockToValue.getOrDefault(symbol, 0.0)))
            .limit(limit)
            .collect(Collectors.toList());
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
