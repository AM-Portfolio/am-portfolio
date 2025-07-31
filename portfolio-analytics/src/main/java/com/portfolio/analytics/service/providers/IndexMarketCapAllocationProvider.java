package com.portfolio.analytics.service.providers;

import com.am.common.amcommondata.model.MarketCapType;
import com.portfolio.analytics.service.AbstractIndexAnalyticsProvider;
import com.portfolio.analytics.service.AnalyticsType;
import com.portfolio.analytics.service.utils.AnalyticsUtils;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.marketdata.service.NseIndicesService;
import com.portfolio.model.analytics.MarketCapAllocation;
import com.portfolio.model.analytics.request.TimeFrameRequest;
import com.portfolio.model.market.MarketData;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Provider for market cap allocation analytics
 */
@Service
@Slf4j
public class IndexMarketCapAllocationProvider extends AbstractIndexAnalyticsProvider<MarketCapAllocation> {

    public IndexMarketCapAllocationProvider(NseIndicesService nseIndicesService, MarketDataService marketDataService, SecurityDetailsService securityDetailsService) {
        super(nseIndicesService, marketDataService, securityDetailsService);
    }

    @Override
    public AnalyticsType getType() {
        return AnalyticsType.MARKET_CAP_ALLOCATION;
    }

    
    // Constants for market cap classification
    private static final double LARGE_CAP_THRESHOLD = 50000000000.0; // 50B
    private static final double MID_CAP_THRESHOLD = 10000000000.0;   // 10B
    private static final double DEFAULT_SHARES_MULTIPLIER = 1000000000.0; // 1B shares
    private static final int TOP_STOCKS_LIMIT = 5;
    
    @Override
    public MarketCapAllocation generateAnalytics(String indexSymbol) {
        log.info("Generating market cap allocation for index: {}", indexSymbol);
        return generateMarketCapAllocation(indexSymbol, null);
    }
    
    @Override
    public MarketCapAllocation generateAnalytics(String indexSymbol, TimeFrameRequest timeFrameRequest) {
        log.info("Generating market cap allocation for index: {} with timeFrame parameters", indexSymbol);
        return generateMarketCapAllocation(indexSymbol, timeFrameRequest);
    }
    
    /**
     * Common implementation for generating market cap allocation analytics
     * 
     * @param indexSymbol The index symbol to analyze
     * @param timeFrameRequest Optional time frame parameters (can be null)
     * @return Market cap allocation analytics
     */
    private MarketCapAllocation generateMarketCapAllocation(String indexSymbol, TimeFrameRequest timeFrameRequest) {
        log.info("Generating market cap allocation for index: {} with timeFrame: {}", indexSymbol, timeFrameRequest);
        
        // Get index symbols
        List<String> indexStockSymbols = getIndexSymbols(indexSymbol);
        if (indexStockSymbols.isEmpty()) {
            log.warn("No stock symbols found for index: {}", indexSymbol);
            return createEmptyAllocation(indexSymbol);
        }
        
        // Fetch market data using AnalyticsUtils
        Map<String, MarketData> marketData = AnalyticsUtils.fetchMarketData(this, indexStockSymbols, timeFrameRequest);
        if (marketData.isEmpty()) {
            log.warn("No market data available for index: {}", indexSymbol);
            return createEmptyAllocation(indexSymbol);
        }
        
        // Process market data and create segments
        Map<String, String> symbolToSegment = classifySymbolsByMarketCap(indexStockSymbols, marketData);
        Map<String, Double> stockMarketCaps = calculateMarketCaps(marketData);
        double totalMarketCap = stockMarketCaps.values().stream().mapToDouble(Double::doubleValue).sum();
        
        // Group stocks by segment and calculate allocation
        Map<String, List<MarketData>> segmentMap = groupMarketDataBySegment(marketData, symbolToSegment);
        List<MarketCapAllocation.CapSegment> segments = createSegments(segmentMap, stockMarketCaps, totalMarketCap, indexStockSymbols, marketData);
        
        // Sort segments by weight percentage (highest to lowest)
        segments.sort(Comparator.comparing(MarketCapAllocation.CapSegment::getWeightPercentage).reversed());
        
        log.info("Generated market cap allocation with {} segments for index: {}", segments.size(), indexSymbol);
        
        return MarketCapAllocation.builder()
            .indexSymbol(indexSymbol)
            .timestamp(Instant.now())
            .segments(segments)
            .build();
    }
    
    /**
     * Create an empty allocation when no data is available
     */
    private MarketCapAllocation createEmptyAllocation(String indexSymbol) {
        return MarketCapAllocation.builder()
            .indexSymbol(indexSymbol)
            .timestamp(Instant.now())
            .segments(Collections.emptyList())
            .build();
    }
    
    /**
     * Classify symbols by market cap type
     */
    private Map<String, String> classifySymbolsByMarketCap(List<String> symbols, Map<String, MarketData> marketData) {
        log.debug("Classifying {} symbols by market cap", symbols.size());
        Map<String, String> marketCapTypeToSegmentName = createMarketCapTypeMapping();
        
        // Get market cap groups from security details service
        Map<String, List<String>> marketCapGroups = securityDetailsService.groupSymbolsByMarketType(symbols);
        log.info("Market cap groups: {}", marketCapGroups.keySet());
        
        // Classify each symbol
        Map<String, String> symbolToSegment = new HashMap<>();
        Map<String, Double> stockMarketCaps = calculateMarketCaps(marketData);
        
        // First, assign based on security details service classification
        for (Map.Entry<String, List<String>> entry : marketCapGroups.entrySet()) {
            String segmentName = marketCapTypeToSegmentName.getOrDefault(entry.getKey(), "Unknown");
            for (String symbol : entry.getValue()) {
                symbolToSegment.put(symbol, segmentName);
            }
        }
        
        // For any remaining symbols, classify based on calculated market cap
        for (String symbol : marketData.keySet()) {
            if (!symbolToSegment.containsKey(symbol)) {
                double marketCap = stockMarketCaps.get(symbol);
                symbolToSegment.put(symbol, classifyMarketCapSize(marketCap));
            }
        }
        
        return symbolToSegment;
    }
    
    /**
     * Create mapping from market cap type enum to display names
     */
    private Map<String, String> createMarketCapTypeMapping() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put(MarketCapType.LARGE_CAP.name(), "Large Cap");
        mapping.put(MarketCapType.MID_CAP.name(), "Mid Cap");
        mapping.put(MarketCapType.SMALL_CAP.name(), "Small Cap");
        mapping.put(MarketCapType.MICRO_CAP.name(), "Micro Cap");
        mapping.put("null", "Unknown"); // Handle null market cap type
        return mapping;
    }
    
    /**
     * Classify a market cap value into a size category
     */
    private String classifyMarketCapSize(double marketCap) {
        if (marketCap > LARGE_CAP_THRESHOLD) {
            return "Large Cap";
        } else if (marketCap > MID_CAP_THRESHOLD) {
            return "Mid Cap";
        } else {
            return "Small Cap";
        }
    }
    
    /**
     * Calculate market caps for all stocks
     */
    private Map<String, Double> calculateMarketCaps(Map<String, MarketData> marketData) {
        log.debug("Calculating market caps for {} stocks", marketData.size());
        Map<String, Double> stockMarketCaps = new HashMap<>();
        
        for (Map.Entry<String, MarketData> entry : marketData.entrySet()) {
            String symbol = entry.getKey();
            MarketData data = entry.getValue();
            
            if (data != null && data.getLastPrice() > 0) {
                double marketCap = AnalyticsUtils.calculateMarketCap(data, DEFAULT_SHARES_MULTIPLIER);
                stockMarketCaps.put(symbol, marketCap);
            } else {
                log.debug("Skipping market cap calculation for {} due to invalid data", symbol);
                stockMarketCaps.put(symbol, 0.0);
            }
        }
        
        return stockMarketCaps;
    }
    
    /**
     * Group market data by segment
     */
    private Map<String, List<MarketData>> groupMarketDataBySegment(
            Map<String, MarketData> marketData, Map<String, String> symbolToSegment) {
        
        Map<String, List<MarketData>> segmentMap = new HashMap<>();
        
        // Initialize segment lists
        for (String segment : new HashSet<>(symbolToSegment.values())) {
            segmentMap.put(segment, new ArrayList<>());
        }
        
        // Group market data by segment
        for (Map.Entry<String, MarketData> entry : marketData.entrySet()) {
            String symbol = entry.getKey();
            MarketData data = entry.getValue();
            String segment = symbolToSegment.getOrDefault(symbol, "Unknown");
            segmentMap.computeIfAbsent(segment, k -> new ArrayList<>()).add(data);
        }
        
        return segmentMap;
    }
    
    /**
     * Create segment objects with allocation data
     */
    private List<MarketCapAllocation.CapSegment> createSegments(
            Map<String, List<MarketData>> segmentMap, 
            Map<String, Double> stockMarketCaps, 
            double totalMarketCap,
            List<String> indexStockSymbols,
            Map<String, MarketData> marketData) {
        
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
                segmentName, segmentStocks, stockMarketCaps, totalMarketCap, indexStockSymbols, marketData);
            
            segments.add(segment);
        }
        
        return segments;
    }
    
    /**
     * Create a single segment with allocation data
     */
    private MarketCapAllocation.CapSegment createSegment(
            String segmentName,
            List<MarketData> segmentStocks,
            Map<String, Double> stockMarketCaps,
            double totalMarketCap,
            List<String> indexStockSymbols,
            Map<String, MarketData> marketData) {
        
        // Calculate segment data
        Map<String, Double> symbolToMarketCap = calculateSegmentMarketCaps(
            segmentStocks, stockMarketCaps, indexStockSymbols, marketData);
        
        double segmentMarketCap = symbolToMarketCap.values().stream()
            .mapToDouble(Double::doubleValue).sum();
        
        // Calculate weight percentage
        double weightPercentage = totalMarketCap > 0 ? 
            (segmentMarketCap / totalMarketCap) * 100 : 0;
        
        // Get top stocks by market cap
        List<String> topStocks = AnalyticsUtils.getTopEntriesByValue(
            symbolToMarketCap, TOP_STOCKS_LIMIT, true);
        
        log.debug("Segment {} has {} stocks with total market cap {} and weight {}%", 
            segmentName, segmentStocks.size(), segmentMarketCap, weightPercentage);
        
        // Create segment object
        return MarketCapAllocation.CapSegment.builder()
            .segmentName(segmentName)
            .weightPercentage(weightPercentage)
            .totalMarketCap(segmentMarketCap)
            .numberOfStocks(segmentStocks.size())
            .topStocks(topStocks)
            .build();
    }
    
    /**
     * Calculate market caps for stocks in a segment
     */
    private Map<String, Double> calculateSegmentMarketCaps(
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
     */
    private String findSymbolByInstrumentToken(long instrumentToken, List<String> symbols, Map<String, MarketData> marketData) {
        return symbols.stream()
            .filter(s -> marketData.containsKey(s) && 
                   marketData.get(s).getInstrumentToken() == instrumentToken)
            .findFirst()
            .orElse(null);
    }
    

}
