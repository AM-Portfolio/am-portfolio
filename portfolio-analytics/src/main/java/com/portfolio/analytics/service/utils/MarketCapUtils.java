package com.portfolio.analytics.service.utils;

import com.am.common.amcommondata.model.MarketCapType;
import com.portfolio.model.analytics.MarketCapAllocation;
import com.portfolio.model.market.MarketData;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for market cap allocation operations
 */
@Slf4j
public class MarketCapUtils {

    // Constants for market cap classification
    public static final double LARGE_CAP_THRESHOLD = 50000000000.0; // 50B
    public static final double MID_CAP_THRESHOLD = 10000000000.0;   // 10B
    public static final double DEFAULT_SHARES_MULTIPLIER = 1000000000.0; // 1B shares
    
    /**
     * Calculate market caps for all stocks
     * @param marketData Map of market data by symbol
     * @return Map of stock symbols to their market caps
     */
    public static Map<String, Double> calculateMarketCaps(Map<String, MarketData> marketData) {
        log.debug("Calculating market caps for {} stocks", marketData.size());
        
        Map<String, Double> stockMarketCaps = new HashMap<>();
        
        marketData.forEach((symbol, data) -> {
            if (data != null) {
                double lastPrice = data.getLastPrice();
                // In a real implementation, we would get outstanding shares from a proper source
                // For now, we'll use a placeholder value or estimate based on available data
                double estimatedShares = DEFAULT_SHARES_MULTIPLIER;
                
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
     * Calculate total market cap
     * @param stockMarketCaps Map of stock symbols to their market caps
     * @return Total market cap
     */
    public static double calculateTotalMarketCap(Map<String, Double> stockMarketCaps) {
        return roundToTwoDecimals(stockMarketCaps.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum());
    }
    
    /**
     * Classify symbols by market cap type
     * @param symbols List of stock symbols
     * @param marketData Map of market data by symbol
     * @return Map of symbol to market cap segment name
     */
    public static Map<String, String> classifySymbolsByMarketCap(List<String> symbols, Map<String, MarketData> marketData) {
        log.debug("Classifying {} symbols by market cap", symbols.size());
        
        Map<String, String> symbolToSegment = new HashMap<>();
        Map<String, Double> stockMarketCaps = calculateMarketCaps(marketData);
        Map<MarketCapType, String> marketCapTypeMapping = createMarketCapTypeMapping();
        
        for (String symbol : symbols) {
            if (marketData.containsKey(symbol)) {
                double marketCap = stockMarketCaps.getOrDefault(symbol, 0.0);
                MarketCapType marketCapType = classifyMarketCapSize(marketCap);
                String segmentName = marketCapTypeMapping.get(marketCapType);
                symbolToSegment.put(symbol, segmentName);
                log.trace("Symbol: {}, Market Cap: {}, Segment: {}", symbol, marketCap, segmentName);
            }
        }
        
        log.debug("Classified {} symbols into market cap segments", symbolToSegment.size());
        return symbolToSegment;
    }
    
    /**
     * Create mapping from market cap type enum to display names
     * @return Map of market cap type to display name
     */
    public static Map<MarketCapType, String> createMarketCapTypeMapping() {
        Map<MarketCapType, String> mapping = new HashMap<>();
        mapping.put(MarketCapType.LARGE_CAP, "Large Cap");
        mapping.put(MarketCapType.MID_CAP, "Mid Cap");
        mapping.put(MarketCapType.SMALL_CAP, "Small Cap");
        return mapping;
    }
    
    /**
     * Classify a market cap value into a size category
     * @param marketCap The market cap value to classify
     * @return The market cap type (LARGE_CAP, MID_CAP, or SMALL_CAP)
     */
    public static MarketCapType classifyMarketCapSize(double marketCap) {
        if (marketCap >= LARGE_CAP_THRESHOLD) {
            return MarketCapType.LARGE_CAP;
        } else if (marketCap >= MID_CAP_THRESHOLD) {
            return MarketCapType.MID_CAP;
        } else {
            return MarketCapType.SMALL_CAP;
        }
    }
    
    /**
     * Group market data by segment
     * @param marketData Map of market data by symbol
     * @param symbolToSegment Map of symbol to segment name
     * @return Map of segment name to list of market data
     */
    public static Map<String, List<MarketData>> groupMarketDataBySegment(
            Map<String, MarketData> marketData, Map<String, String> symbolToSegment) {
        
        log.debug("Grouping market data by segment");
        
        Map<String, List<MarketData>> segmentMap = new HashMap<>();
        
        // Initialize segments
        for (String segment : new HashSet<>(symbolToSegment.values())) {
            segmentMap.put(segment, new ArrayList<>());
        }
        
        // Group stocks by segment
        for (Map.Entry<String, String> entry : symbolToSegment.entrySet()) {
            String symbol = entry.getKey();
            String segment = entry.getValue();
            
            if (marketData.containsKey(symbol)) {
                segmentMap.get(segment).add(marketData.get(symbol));
            }
        }
        
        log.debug("Grouped market data into {} segments", segmentMap.size());
        return segmentMap;
    }
    
    /**
     * Create segments with allocation data
     * @param segmentMap Map of segment name to list of market data
     * @param stockMarketCaps Map of stock symbols to their market caps
     * @param totalMarketCap Total market cap
     * @param indexStockSymbols List of index stock symbols
     * @param marketData Map of market data by symbol
     * @return List of cap segments
     */
    public static List<MarketCapAllocation.CapSegment> createSegments(
            Map<String, List<MarketData>> segmentMap, 
            Map<String, Double> stockMarketCaps, 
            double totalMarketCap,
            List<String> indexStockSymbols,
            Map<String, MarketData> marketData, int limit) {
        
        List<MarketCapAllocation.CapSegment> segments = new ArrayList<>();
        
        for (Map.Entry<String, List<MarketData>> entry : segmentMap.entrySet()) {
            String segmentName = entry.getKey();
            List<MarketData> segmentStocks = entry.getValue();
            
            if (segmentStocks.isEmpty()) {
                log.debug("Skipping empty segment: {}", segmentName);
                continue; // Skip empty segments
            }
            
            // Create segment object
            MarketCapAllocation.CapSegment segment = createSegment(
                segmentName, segmentStocks, stockMarketCaps, totalMarketCap, indexStockSymbols, marketData, limit);
            
            segments.add(segment);
        }
        
        return segments;
    }
    
    /**
     * Create a single segment with allocation data
     * @param segmentName Name of the segment
     * @param segmentStocks List of market data for stocks in the segment
     * @param stockMarketCaps Map of stock symbols to their market caps
     * @param totalMarketCap Total market cap
     * @param indexStockSymbols List of index stock symbols
     * @param marketData Map of market data by symbol
     * @return Cap segment
     */
    public static MarketCapAllocation.CapSegment createSegment(
            String segmentName,
            List<MarketData> segmentStocks,
            Map<String, Double> stockMarketCaps,
            double totalMarketCap,
            List<String> indexStockSymbols,
            Map<String, MarketData> marketData, int limit) {
        
        // Calculate segment data
        Map<String, Double> symbolToMarketCap = calculateSegmentMarketCaps(
            segmentStocks, stockMarketCaps, indexStockSymbols, marketData);
        
        double segmentMarketCap = symbolToMarketCap.values().stream()
            .mapToDouble(Double::doubleValue).sum();
        
        // Calculate weight percentage
        double weightPercentage = calculateWeightPercentage(segmentMarketCap, totalMarketCap);
        
        // Get top stocks by market cap
        List<String> topStocks = getTopStocksByMarketCap(symbolToMarketCap, limit);
        
        log.debug("Segment {} has {} stocks with total market cap {} and weight {}%", 
            segmentName, segmentStocks.size(), segmentMarketCap, weightPercentage);
        
        // Create segment object
        return MarketCapAllocation.CapSegment.builder()
            .segmentName(segmentName)
            .weightPercentage(roundToTwoDecimals(weightPercentage))
            .segmentValue(roundToTwoDecimals(segmentMarketCap))
            .numberOfStocks(segmentStocks.size())
            .topStocks(topStocks)
            .build();
    }
    
    /**
     * Calculate market caps for stocks in a segment
     * @param segmentStocks List of market data for stocks in the segment
     * @param stockMarketCaps Map of stock symbols to their market caps
     * @param indexStockSymbols List of index stock symbols
     * @param marketData Map of market data by symbol
     * @return Map of symbol to market cap for stocks in the segment
     */
    public static Map<String, Double> calculateSegmentMarketCaps(
            List<MarketData> segmentStocks, 
            Map<String, Double> stockMarketCaps,
            List<String> indexStockSymbols,
            Map<String, MarketData> marketData) {
        
        Map<String, Double> symbolToMarketCap = new HashMap<>();
        
        for (MarketData stock : segmentStocks) {
            if (stock == null || stock.getInstrumentToken() == 0) {
                continue;
            }
            
            // Get symbol from instrument token
            String symbol = findSymbolByInstrumentToken(stock.getInstrumentToken(), indexStockSymbols, marketData);
            
            if (symbol != null) {
                double marketCap = stockMarketCaps.getOrDefault(symbol, 0.0);
                symbolToMarketCap.put(symbol, marketCap);
            }
        }
        
        return symbolToMarketCap;
    }
    
    /**
     * Find a symbol by its instrument token
     * @param instrumentToken The instrument token to search for
     * @param symbols List of symbols to search in
     * @param marketData Map of market data by symbol
     * @return The symbol with the matching instrument token, or null if not found
     */
    public static String findSymbolByInstrumentToken(long instrumentToken, List<String> symbols, Map<String, MarketData> marketData) {
        return symbols.stream()
            .filter(s -> marketData.containsKey(s) && 
                   marketData.get(s).getInstrumentToken() == instrumentToken)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Calculate weight percentage
     * @param segmentValue Value of the segment
     * @param totalValue Total value
     * @return Weight percentage
     */
    public static double calculateWeightPercentage(double segmentValue, double totalValue) {
        return roundToTwoDecimals(totalValue > 0 ? (segmentValue / totalValue) * 100 : 0);
    }
    
    /**
     * Get top stocks by market cap
     * @param symbolToMarketCap Map of symbol to market cap
     * @param limit Number of top stocks to return
     * @return List of top stock symbols
     */
    public static List<String> getTopStocksByMarketCap(Map<String, Double> symbolToMarketCap, int limit) {
        return symbolToMarketCap.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(limit)
            .map(Map.Entry::getKey)
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
