package com.portfolio.analytics.service.providers;

import com.am.common.amcommondata.model.MarketCapType;
import com.portfolio.analytics.service.AbstractIndexAnalyticsProvider;
import com.portfolio.analytics.service.AnalyticsType;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.model.MarketDataResponse;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.marketdata.service.NseIndicesService;
import com.portfolio.model.analytics.MarketCapAllocation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Provider for market cap allocation analytics
 */
@Service
@Slf4j
public class MarketCapAllocationProvider extends AbstractIndexAnalyticsProvider<MarketCapAllocation> {

    public MarketCapAllocationProvider(NseIndicesService nseIndicesService, MarketDataService marketDataService, SecurityDetailsService securityDetailsService) {
        super(nseIndicesService, marketDataService, securityDetailsService);
    }

    @Override
    public AnalyticsType getType() {
        return AnalyticsType.MARKET_CAP_ALLOCATION;
    }

    @Override
    public MarketCapAllocation generateAnalytics(String indexSymbol) {
        log.info("Calculating market cap allocations for index: {}", indexSymbol);
        
        var indexStockSymbols = getIndexSymbols(indexSymbol);
        if (indexStockSymbols.isEmpty()) {
            log.warn("No stock symbols found for index: {}", indexSymbol);
            return MarketCapAllocation.builder()
                .indexSymbol(indexSymbol)
                .timestamp(Instant.now())
                .segments(Collections.emptyList())
                .build();
        }
        
        // Fetch market data for all stocks in the index
        var marketData = getMarketData(indexStockSymbols);
        if (marketData.isEmpty()) {
            log.warn("No market data available for index: {}", indexSymbol);
            return MarketCapAllocation.builder()
                .indexSymbol(indexSymbol)
                .timestamp(Instant.now())
                .segments(Collections.emptyList())
                .build();
        }
        
        // Use SecurityDetailsService to group symbols by market cap type
        Map<String, List<String>> marketCapGroups = securityDetailsService.groupSymbolsByMarketType(indexStockSymbols);
        
        log.info("Market cap groups for index {}: {}", indexSymbol, marketCapGroups.keySet());
        
        // Create a mapping for market cap type enum to segment name
        Map<String, String> marketCapTypeToSegmentName = new HashMap<>();
        marketCapTypeToSegmentName.put(MarketCapType.LARGE_CAP.name(), "Large Cap");
        marketCapTypeToSegmentName.put(MarketCapType.MID_CAP.name(), "Mid Cap");
        marketCapTypeToSegmentName.put(MarketCapType.SMALL_CAP.name(), "Small Cap");
        marketCapTypeToSegmentName.put(MarketCapType.MICRO_CAP.name(), "Micro Cap");
        marketCapTypeToSegmentName.put("null", "Unknown"); // Handle null market cap type
        
        // Calculate market cap for each stock
        double totalMarketCap = 0.0;
        Map<String, Double> stockMarketCaps = new HashMap<>();
        Map<String, String> symbolToSegment = new HashMap<>(); // Map to store symbol to segment mapping
        
        for (String symbol : marketData.keySet()) {
            MarketDataResponse data = marketData.get(symbol);
            
            // Calculate market cap using just the price as a proxy
            // In a real implementation, would multiply by actual outstanding shares
            double marketCap = data.getLastPrice() * 1000000000.0; // Assuming 1B shares for all stocks
            stockMarketCaps.put(symbol, marketCap);
            totalMarketCap += marketCap;
            
            // Find which segment this symbol belongs to
            for (Map.Entry<String, List<String>> entry : marketCapGroups.entrySet()) {
                if (entry.getValue().contains(symbol)) {
                    String segmentName = marketCapTypeToSegmentName.getOrDefault(entry.getKey(), "Unknown");
                    symbolToSegment.put(symbol, segmentName);
                    break;
                }
            }
            
            // If symbol wasn't found in any group, assign based on calculated market cap
            if (!symbolToSegment.containsKey(symbol)) {
                String segment;
                if (marketCap > 50000000000.0) { // > 50B
                    segment = "Large Cap";
                } else if (marketCap > 10000000000.0) { // > 10B
                    segment = "Mid Cap";
                } else {
                    segment = "Small Cap";
                }
                symbolToSegment.put(symbol, segment);
            }
        }
        
        // Group market data by segment
        Map<String, List<MarketDataResponse>> segmentMap = new HashMap<>();
        for (String segment : new HashSet<>(symbolToSegment.values())) {
            segmentMap.put(segment, new ArrayList<>());
        }
        
        for (String symbol : marketData.keySet()) {
            String segment = symbolToSegment.getOrDefault(symbol, "Unknown");
            segmentMap.computeIfAbsent(segment, k -> new ArrayList<>()).add(marketData.get(symbol));
        }
        
        // Calculate allocation percentages and create segment objects
        List<MarketCapAllocation.CapSegment> segments = new ArrayList<>();
        for (Map.Entry<String, List<MarketDataResponse>> entry : segmentMap.entrySet()) {
            String segmentName = entry.getKey();
            List<MarketDataResponse> segmentStocks = entry.getValue();
            
            if (segmentStocks.isEmpty()) {
                continue; // Skip empty segments
            }
            
            // Calculate total market cap for this segment
            double segmentMarketCap = 0.0;
            Map<String, Double> symbolToMarketCap = new HashMap<>();
            
            for (MarketDataResponse stock : segmentStocks) {
                // Get symbol from instrument token
                String symbol = indexStockSymbols.stream()
                    .filter(s -> marketData.containsKey(s) && marketData.get(s).getInstrumentToken() == stock.getInstrumentToken())
                    .findFirst()
                    .orElse(null);
                
                if (symbol != null) {
                    double marketCap = stockMarketCaps.getOrDefault(symbol, 0.0);
                    segmentMarketCap += marketCap;
                    symbolToMarketCap.put(symbol, marketCap);
                }
            }
            
            // Calculate weight percentage
            double weightPercentage = totalMarketCap > 0 ? (segmentMarketCap / totalMarketCap) * 100 : 0;
            
            // Get top stocks by market cap for this segment
            List<String> topStocks = symbolToMarketCap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5)  // Top 5 stocks
                .map(Map.Entry::getKey)
                .toList();
            
            segments.add(MarketCapAllocation.CapSegment.builder()
                .segmentName(segmentName)
                .weightPercentage(weightPercentage)
                .totalMarketCap(segmentMarketCap)
                .numberOfStocks(segmentStocks.size())
                .topStocks(topStocks)
                .build());
        }
        
        // Sort segments by weight percentage (highest to lowest)
        segments.sort(Comparator.comparing(MarketCapAllocation.CapSegment::getWeightPercentage).reversed());
        
        return MarketCapAllocation.builder()
            .indexSymbol(indexSymbol)
            .timestamp(Instant.now())
            .segments(segments)
            .build();
    }
    

}
