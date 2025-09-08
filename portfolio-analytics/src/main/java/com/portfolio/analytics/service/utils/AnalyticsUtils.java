package com.portfolio.analytics.service.utils;

import com.portfolio.analytics.service.AbstractAnalyticsProvider;
import com.portfolio.model.analytics.request.TimeFrameRequest;
import com.portfolio.model.market.MarketData;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for common analytics operations used across different analytics providers
 */
@Slf4j
public class AnalyticsUtils {

    /**
     * Calculate market cap for a stock based on its price
     * In a real implementation, would multiply by actual outstanding shares
     * 
     * @param marketData The market data for the stock
     * @param sharesMultiplier Multiplier to simulate outstanding shares (default: 1B)
     * @return Estimated market cap
     */
    public static double calculateMarketCap(MarketData marketData, double sharesMultiplier) {
        return marketData.getLastPrice() * sharesMultiplier;
    }

    /**
     * Find symbol by instrument token in a map of market data
     * 
     * @param instrumentToken The instrument token to find
     * @param marketData Map of symbols to market data
     * @return The symbol if found, null otherwise
     */
    public static String findSymbolByInstrumentToken(long instrumentToken, Map<String, MarketData> marketData) {
        return marketData.entrySet().stream()
            .filter(entry -> entry.getValue().getInstrumentToken() == instrumentToken)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }

    /**
     * Get top N entries from a map by value
     * 
     * @param map The map to sort
     * @param limit Maximum number of entries to return
     * @param descending If true, sort in descending order (highest values first)
     * @param <K> Key type
     * @param <V> Value type that extends Comparable
     * @return List of keys sorted by their values
     */
    public static <K, V extends Comparable<V>> List<K> getTopEntriesByValue(
            Map<K, V> map, int limit, boolean descending) {
        
        Comparator<Map.Entry<K, V>> comparator = Map.Entry.comparingByValue();
        if (descending) {
            comparator = comparator.reversed();
        }
        
        return map.entrySet().stream()
            .sorted(comparator)
            .limit(limit)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    /**
     * Calculate weighted average based on values and weights
     * 
     * @param values The values to average
     * @param weights The weights for each value
     * @return Weighted average or 0 if inputs are invalid
     */
    public static double calculateWeightedAverage(List<Double> values, List<Double> weights) {
        if (values == null || weights == null || values.size() != weights.size() || values.isEmpty()) {
            return 0.0;
        }
        
        double weightedSum = 0.0;
        double totalWeight = 0.0;
        
        for (int i = 0; i < values.size(); i++) {
            weightedSum += values.get(i) * weights.get(i);
            totalWeight += weights.get(i);
        }
        
        return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
    }

    /**
     * Group market data by a classification function
     * 
     * @param marketData Map of symbols to market data
     * @param classifier Function that returns the group for a symbol
     * @param <T> Type of the group identifier
     * @return Map of groups to lists of market data
     */
    public static <T> Map<T, List<MarketData>> groupMarketDataBy(
            Map<String, MarketData> marketData, 
            Map<String, T> symbolToGroupMap) {
        
        Map<T, List<MarketData>> groupedData = new HashMap<>();
        
        for (Map.Entry<String, MarketData> entry : marketData.entrySet()) {
            String symbol = entry.getKey();
            MarketData data = entry.getValue();
            T group = symbolToGroupMap.get(symbol);
            
            if (group != null) {
                groupedData.computeIfAbsent(group, k -> new ArrayList<>()).add(data);
            }
        }
        
        return groupedData;
    }
    
    /**
     * Fetch market data with or without time frame parameters
     * 
     * @param provider The analytics provider to use for fetching data
     * @param symbols List of symbols to fetch data for
     * @param timeFrameRequest Optional time frame parameters (can be null)
     * @param <T> Type of analytics data returned by the provider
     * @param <I> Type of identifier used by the provider
     * @return Map of symbols to market data
     */
    public static <T, I> Map<String, MarketData> fetchMarketData(
            AbstractAnalyticsProvider<T, I> provider,
            List<String> symbols,
            TimeFrameRequest timeFrameRequest) {
        
        if (symbols == null || symbols.isEmpty()) {
            log.warn("No symbols provided for market data fetch");
            return Collections.emptyMap();
        }
        
        log.info("Fetching current market data for {} symbols", symbols.size());
        Map<String, MarketData>  marketData = provider.getMarketData(symbols);
        // if (timeFrameRequest != null) {
        //     log.info("Fetching historical data for {} symbols with time frame", symbols.size());
        //     marketData = provider.getHistoricalData(symbols, timeFrameRequest);
        // } else {
        //     log.info("Fetching current market data for {} symbols", symbols.size());
        //     marketData = provider.getMarketData(symbols);
        // }
        
        if (marketData == null || marketData.isEmpty()) {
            log.warn("No market data available for the provided symbols");
            return Collections.emptyMap();
        }
        
        return marketData;
    }
}
