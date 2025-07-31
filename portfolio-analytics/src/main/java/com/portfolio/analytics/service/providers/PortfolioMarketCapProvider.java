package com.portfolio.analytics.service.providers;

import com.am.common.amcommondata.model.MarketCapType;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.analytics.service.AbstractPortfolioAnalyticsProvider;
import com.portfolio.analytics.service.AnalyticsType;
import com.portfolio.analytics.service.utils.AnalyticsUtils;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.analytics.MarketCapAllocation;
import com.portfolio.model.analytics.request.TimeFrameRequest;
import com.portfolio.model.market.MarketData;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Provider for portfolio market cap allocation analytics
 */
@Service
@Slf4j
public class PortfolioMarketCapProvider extends AbstractPortfolioAnalyticsProvider<MarketCapAllocation> {

    public PortfolioMarketCapProvider(PortfolioService portfolioService, MarketDataService marketDataService, SecurityDetailsService securityDetailsService) {
        super(portfolioService, marketDataService, securityDetailsService);
    }

    @Override
    public AnalyticsType getType() {
        return AnalyticsType.MARKET_CAP_ALLOCATION;
    }

    

    @Override
    public MarketCapAllocation generateAnalytics(String portfolioId) {
        log.info("Calculating market cap allocations for portfolio: {}", portfolioId);
        return generateMarketCapAllocation(portfolioId, null);
    }
    
    @Override
    public MarketCapAllocation generateAnalytics(String portfolioId, TimeFrameRequest timeFrameRequest) {
        log.info("Calculating market cap allocations for portfolio: {} with time frame", portfolioId);
        return generateMarketCapAllocation(portfolioId, timeFrameRequest);
    }
    
    /**
     * Common implementation for generating market cap allocation analytics
     * 
     * @param portfolioId The portfolio ID to analyze
     * @param timeFrameRequest Optional time frame parameters (can be null)
     * @return Market cap allocation analytics
     */
    private MarketCapAllocation generateMarketCapAllocation(String portfolioId, TimeFrameRequest timeFrameRequest) {
        // Get portfolio data
        PortfolioModelV1 portfolio = getPortfolio(portfolioId);
        if (portfolio == null || portfolio.getEquityModels() == null || portfolio.getEquityModels().isEmpty()) {
            log.warn("No portfolio or holdings found for ID: {}", portfolioId);
            return createEmptyResult(portfolioId);
        }
        
        // Get symbols from portfolio holdings
        List<String> portfolioSymbols = getPortfolioSymbols(portfolio);
        if (portfolioSymbols.isEmpty()) {
            log.warn("No stock symbols found in portfolio: {}", portfolioId);
            return createEmptyResult(portfolioId);
        }
        
        // Fetch market data for all stocks in the portfolio
        Map<String, MarketData> marketData;
        if (timeFrameRequest != null) {
            marketData = getHistoricalData(portfolioSymbols, timeFrameRequest);
        } else {
            marketData = getMarketData(portfolioSymbols);
        }
        
        if (marketData.isEmpty()) {
            log.warn("No market data available for portfolio: {}", portfolioId);
            return createEmptyResult(portfolioId);
        }
        
        // Create a map of symbol to holding quantity
        Map<String, Double> symbolToQuantity = createSymbolToQuantityMap(portfolio);
        
        // Use SecurityDetailsService to group symbols by market cap type
        Map<String, List<String>> marketCapGroups = securityDetailsService.groupSymbolsByMarketType(portfolioSymbols);
        log.info("Market cap groups for portfolio {}: {}", portfolioId, marketCapGroups.keySet());
        
        // Create a mapping for market cap type enum to segment name
        Map<String, String> marketCapTypeToSegmentName = createMarketCapTypeMapping();
        
        // Calculate market values and assign segments
        Map<String, Double> stockMarketValues = new HashMap<>();
        Map<String, String> symbolToSegment = new HashMap<>();
        double totalPortfolioValue = calculateMarketValuesAndAssignSegments(
                portfolioSymbols, marketData, symbolToQuantity, marketCapGroups, 
                marketCapTypeToSegmentName, stockMarketValues, symbolToSegment);
        
        // Group symbols by segment
        Map<String, List<String>> segmentToSymbols = groupSymbolsBySegment(symbolToSegment);
        
        // Create segment objects with allocation percentages
        List<MarketCapAllocation.CapSegment> segments = createCapSegments(
                segmentToSymbols, stockMarketValues, totalPortfolioValue);
        
        log.info("Generated market cap allocation with {} segments for portfolio: {}", segments.size(), portfolioId);
        
        return MarketCapAllocation.builder()
            .portfolioId(portfolioId)
            .timestamp(Instant.now())
            .segments(segments)
            .build();
    }
    
    /**
     * Create empty result when no data is available
     */
    private MarketCapAllocation createEmptyResult(String portfolioId) {
        return MarketCapAllocation.builder()
            .portfolioId(portfolioId)
            .timestamp(Instant.now())
            .segments(Collections.emptyList())
            .build();
    }
    
    /**
     * Create a map of symbol to holding quantity
     */
    private Map<String, Double> createSymbolToQuantityMap(PortfolioModelV1 portfolio) {
        return portfolio.getEquityModels().stream()
            .collect(Collectors.toMap(
                EquityModel::getSymbol,
                EquityModel::getQuantity,
                (q1, q2) -> q1 + q2  // In case of duplicate symbols, sum quantities
            ));
    }
    
    /**
     * Create mapping from market cap type to display name
     */
    private Map<String, String> createMarketCapTypeMapping() {
        Map<String, String> marketCapTypeToSegmentName = new HashMap<>();
        marketCapTypeToSegmentName.put(MarketCapType.LARGE_CAP.name(), "Large Cap");
        marketCapTypeToSegmentName.put(MarketCapType.MID_CAP.name(), "Mid Cap");
        marketCapTypeToSegmentName.put(MarketCapType.SMALL_CAP.name(), "Small Cap");
        marketCapTypeToSegmentName.put(MarketCapType.MICRO_CAP.name(), "Micro Cap");
        marketCapTypeToSegmentName.put("null", "Unknown"); // Handle null market cap type
        return marketCapTypeToSegmentName;
    }
    
    /**
     * Calculate market values and assign segments to symbols
     */
    private double calculateMarketValuesAndAssignSegments(
            List<String> portfolioSymbols, 
            Map<String, MarketData> marketData,
            Map<String, Double> symbolToQuantity,
            Map<String, List<String>> marketCapGroups,
            Map<String, String> marketCapTypeToSegmentName,
            Map<String, Double> stockMarketValues,
            Map<String, String> symbolToSegment) {
        log.debug("Calculating market values and assigning segments for {} symbols", portfolioSymbols.size());
        
        double totalPortfolioValue = 0.0;
        
        for (String symbol : portfolioSymbols) {
            MarketData data = marketData.get(symbol);
            if (data == null) {
                log.warn("No market data for symbol: {}", symbol);
                continue;
            }
            
            double quantity = symbolToQuantity.getOrDefault(symbol, 0.0);
            if (quantity <= 0) {
                log.warn("Invalid quantity for symbol: {}", symbol);
                continue;
            }
            
            // Calculate market value of this holding
            double marketValue = data.getLastPrice() * quantity;
            stockMarketValues.put(symbol, marketValue);
            totalPortfolioValue += marketValue;
            
            // Assign segment based on market cap groups
            assignSegmentToSymbol(symbol, data, marketCapGroups, marketCapTypeToSegmentName, symbolToSegment);
        }
        
        return totalPortfolioValue;
    }
    
    /**
     * Assign a market cap segment to a symbol
     */
    private void assignSegmentToSymbol(
            String symbol, 
            MarketData data, 
            Map<String, List<String>> marketCapGroups,
            Map<String, String> marketCapTypeToSegmentName,
            Map<String, String> symbolToSegment) {
        
        // First try to find the symbol in the market cap groups
        for (Map.Entry<String, List<String>> entry : marketCapGroups.entrySet()) {
            if (entry.getValue().contains(symbol)) {
                String segmentName = marketCapTypeToSegmentName.getOrDefault(entry.getKey(), "Unknown");
                symbolToSegment.put(symbol, segmentName);
                return;
            }
        }
        
        // If symbol wasn't found in any group, assign based on calculated market cap
        if (!symbolToSegment.containsKey(symbol)) {
            String segment = estimateMarketCapSegment(data.getLastPrice());
            symbolToSegment.put(symbol, segment);
        }
    }
    
    /**
     * Estimate market cap segment based on price (assuming 1B shares)
     */
    private String estimateMarketCapSegment(double price) {
        double marketCap = price * 1000000000.0; // Assuming 1B shares for estimation
        
        if (marketCap > 50000000000.0) { // > 50B
            return "Large Cap";
        } else if (marketCap > 10000000000.0) { // > 10B
            return "Mid Cap";
        } else {
            return "Small Cap";
        }
    }
    
    /**
     * Group symbols by their assigned segment
     */
    private Map<String, List<String>> groupSymbolsBySegment(Map<String, String> symbolToSegment) {
        log.debug("Grouping {} symbols by segment", symbolToSegment.size());
        Map<String, List<String>> segmentToSymbols = new HashMap<>();
        
        for (Map.Entry<String, String> entry : symbolToSegment.entrySet()) {
            String symbol = entry.getKey();
            String segment = entry.getValue();
            segmentToSymbols.computeIfAbsent(segment, k -> new ArrayList<>()).add(symbol);
        }
        
        return segmentToSymbols;
    }
    
    /**
     * Create cap segments with allocation percentages
     */
    private List<MarketCapAllocation.CapSegment> createCapSegments(
            Map<String, List<String>> segmentToSymbols,
            Map<String, Double> stockMarketValues,
            double totalPortfolioValue) {
        log.debug("Creating cap segments for {} market cap groups", segmentToSymbols.size());
        
        List<MarketCapAllocation.CapSegment> segments = new ArrayList<>();
        
        for (Map.Entry<String, List<String>> entry : segmentToSymbols.entrySet()) {
            String segmentName = entry.getKey();
            List<String> segmentSymbols = entry.getValue();
            
            if (segmentSymbols.isEmpty()) {
                continue; // Skip empty segments
            }
            
            // Calculate segment metrics
            SegmentMetrics metrics = calculateSegmentMetrics(
                    segmentSymbols, stockMarketValues, totalPortfolioValue);
            
            segments.add(MarketCapAllocation.CapSegment.builder()
                .segmentName(segmentName)
                .weightPercentage(metrics.weightPercentage)
                .totalMarketCap(metrics.segmentMarketValue)
                .numberOfStocks(segmentSymbols.size())
                .topStocks(metrics.topStocks)
                .build());
        }
        
        // Sort segments by weight percentage (highest to lowest)
        segments.sort(Comparator.comparing(MarketCapAllocation.CapSegment::getWeightPercentage).reversed());
        
        log.info("Created {} market cap segments", segments.size());
        
        return segments;
    }
    
    /**
     * Calculate metrics for a segment
     */
    private SegmentMetrics calculateSegmentMetrics(
            List<String> segmentSymbols,
            Map<String, Double> stockMarketValues,
            double totalPortfolioValue) {
        
        // Calculate total market value for this segment
        Map<String, Double> symbolToMarketValue = new HashMap<>();
        double segmentMarketValue = 0.0;
        
        for (String symbol : segmentSymbols) {
            double marketValue = stockMarketValues.getOrDefault(symbol, 0.0);
            segmentMarketValue += marketValue;
            symbolToMarketValue.put(symbol, marketValue);
        }
        
        // Calculate weight percentage
        double weightPercentage = totalPortfolioValue > 0 ? (segmentMarketValue / totalPortfolioValue) * 100 : 0;
        
        // Get top stocks by market value for this segment
        List<String> topStocks = AnalyticsUtils.getTopEntriesByValue(symbolToMarketValue, 5, true);
        
        return new SegmentMetrics(segmentMarketValue, weightPercentage, topStocks);
    }
    
    /**
     * Helper class to hold segment metrics
     */
    private static class SegmentMetrics {
        final double segmentMarketValue;
        final double weightPercentage;
        final List<String> topStocks;
        
        SegmentMetrics(double segmentMarketValue, double weightPercentage, List<String> topStocks) {
            this.segmentMarketValue = segmentMarketValue;
            this.weightPercentage = weightPercentage;
            this.topStocks = topStocks;
        }
    }
}
