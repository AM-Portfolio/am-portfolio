package com.portfolio.analytics.service.providers;

import com.portfolio.analytics.service.AbstractIndexAnalyticsProvider;
import com.portfolio.analytics.service.AnalyticsType;
import com.portfolio.marketdata.model.MarketDataResponse;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.marketdata.service.NseIndicesService;
import com.portfolio.model.analytics.MarketCapAllocation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Provider for market cap allocation analytics
 */
@Service
@Slf4j
public class MarketCapAllocationProvider extends AbstractIndexAnalyticsProvider<MarketCapAllocation> {

    public MarketCapAllocationProvider(NseIndicesService nseIndicesService, MarketDataService marketDataService) {
        super(nseIndicesService, marketDataService);
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
        
        // Define market cap segments
        Map<String, List<MarketDataResponse>> segmentMap = new HashMap<>();
        segmentMap.put("Large Cap", new ArrayList<>());
        segmentMap.put("Mid Cap", new ArrayList<>());
        segmentMap.put("Small Cap", new ArrayList<>());
        
        // Group stocks by market cap segment
        // In a real implementation, this would use actual market cap data
        // For now, we'll use a simplified approach with mock data
        double totalMarketCap = 0.0;
        Map<String, Double> stockMarketCaps = new HashMap<>();
        Map<Long, String> instrumentToSymbol = new HashMap<>(); // Map to store instrument token to symbol mapping
        
        for (String symbol : marketData.keySet()) {
            MarketDataResponse data = marketData.get(symbol);
            // Store the mapping of instrument token to symbol
            instrumentToSymbol.put(data.getInstrumentToken(), symbol);
            
            // Mock market cap calculation (price * assumed outstanding shares)
            // In a real implementation, get actual shares outstanding from a data source
            double mockShares = getMockOutstandingShares(symbol);
            double marketCap = data.getLastPrice() * mockShares;
            stockMarketCaps.put(symbol, marketCap);
            totalMarketCap += marketCap;
            
            // Assign to segment based on market cap
            String segment;
            if (marketCap > 50000000000.0) { // > 50B
                segment = "Large Cap";
            } else if (marketCap > 10000000000.0) { // > 10B
                segment = "Mid Cap";
            } else {
                segment = "Small Cap";
            }
            
            segmentMap.get(segment).add(data);
        }
        
        // Calculate allocation percentages and create segment objects
        List<MarketCapAllocation.CapSegment> segments = new ArrayList<>();
        for (Map.Entry<String, List<MarketDataResponse>> entry : segmentMap.entrySet()) {
            String segmentName = entry.getKey();
            List<MarketDataResponse> segmentStocks = entry.getValue();
            
            // Calculate total market cap for this segment
            double segmentMarketCap = 0.0;
            Map<String, Double> symbolToMarketCap = new HashMap<>();
            
            for (MarketDataResponse stock : segmentStocks) {
                // Get the symbol from our mapping using instrument token
                String symbol = instrumentToSymbol.get(stock.getInstrumentToken());
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
    
    /**
     * Get mock outstanding shares for a stock (for demonstration)
     * In a real implementation, this would come from a stock data service
     */
    private double getMockOutstandingShares(String symbol) {
        // Simple mock implementation - in real code, get this from a proper source
        if (symbol.startsWith("RELIANCE") || symbol.startsWith("TCS")) {
            return 8000000000.0; // 8 billion shares
        } else if (symbol.startsWith("HDFC") || symbol.startsWith("INFY")) {
            return 5000000000.0; // 5 billion shares
        } else if (symbol.startsWith("ITC") || symbol.startsWith("SBI")) {
            return 3000000000.0; // 3 billion shares
        } else {
            return 1000000000.0; // 1 billion shares
        }
    }
}
