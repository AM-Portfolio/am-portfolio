package com.portfolio.service;

import com.portfolio.model.analytics.GainerLoser;
import com.portfolio.model.analytics.Heatmap;
import com.portfolio.model.analytics.MarketCapAllocation;
import com.portfolio.model.analytics.SectorAllocation;
import com.portfolio.redis.service.StockIndicesRedisService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.marketdata.service.NseIndicesService;
import com.portfolio.marketdata.model.MarketDataResponse;
import com.portfolio.marketdata.model.indices.IndexConstituent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Service for generating various analytics based on index data
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IndexAnalyticsService {
    private final NseIndicesService nseIndicesService;
    private final MarketDataService marketDataService;
    
    /**
     * Fetch all stock symbols for a given index
     * @param indexSymbol The index symbol to fetch stock symbols for
     * @return List of stock symbols in the index
     */
    public List<String> getIndexSymbols(String indexSymbol) {
        log.info("Fetching symbols for index: {}", indexSymbol);
        try {
            var indexConstituents = nseIndicesService.getIndexConstituents(indexSymbol);
            if (indexConstituents != null) {
                log.info("Found {} symbols for index {}", indexConstituents.size(), indexSymbol);
                return indexConstituents.stream()
                    .map(IndexConstituent::getSymbol)
                    .toList();
            } else {
                log.warn("No data found for index: {}", indexSymbol);
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Error fetching symbols for index: {}", indexSymbol, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Generate a heatmap for sectors based on their performance
     * @param indexSymbol The index symbol to generate heatmap for
     * @return Heatmap containing sector performances
     */
    public Heatmap generateSectorHeatmap(String indexSymbol) {
        log.info("Generating sector heatmap for index: {}", indexSymbol);

        var indexStockSymbols = getIndexSymbols(indexSymbol);
        if (indexStockSymbols.isEmpty()) {
            log.warn("No stock symbols found for index: {}", indexSymbol);
            return Heatmap.builder()
                .indexSymbol(indexSymbol)
                .timestamp(Instant.now())
                .sectors(Collections.emptyList())
                .build();
        }
        
        log.info("Fetching market data for {} symbols in index {}", indexStockSymbols.size(), indexSymbol);
        var marketData = marketDataService.getOhlcData(indexStockSymbols);
        if (marketData.isEmpty()) {
            log.warn("No market data available for index: {}", indexSymbol);
            return Heatmap.builder()
                .indexSymbol(indexSymbol)
                .timestamp(Instant.now())
                .sectors(Collections.emptyList())
                .build();
        }
        
        // Group stocks by sector and calculate performance
        Map<String, List<MarketDataResponse>> sectorMap = new HashMap<>();
        
        // Get stock metadata from Redis or another source to map symbols to sectors
        // For now, we'll use a simplified approach with mock sector data
        for (String symbol : marketData.keySet()) {
            // In a real implementation, you would get the sector from a stock metadata service
            // For now, we'll assign mock sectors based on symbol prefix
            String sector = getMockSectorForSymbol(symbol);
            
            sectorMap.computeIfAbsent(sector, k -> new ArrayList<>())
                .add(marketData.get(symbol));
        }
        
        // Calculate performance for each sector
        List<Heatmap.SectorPerformance> sectorPerformances = new ArrayList<>();
        for (Map.Entry<String, List<MarketDataResponse>> entry : sectorMap.entrySet()) {
            String sectorName = entry.getKey();
            List<MarketDataResponse> sectorStocks = entry.getValue();
            
            // Calculate average performance for the sector
            double totalPerformance = 0.0;
            double totalChangePercent = 0.0;
            
            for (MarketDataResponse stock : sectorStocks) {
                double closePrice = stock.getOhlc().getClose();
                double openPrice = stock.getOhlc().getOpen();
                
                if (openPrice > 0) {
                    double changePercent = ((stock.getLastPrice() - openPrice) / openPrice) * 100;
                    totalChangePercent += changePercent;
                    
                    // Performance score based on price movement relative to previous close
                    double performanceScore = ((stock.getLastPrice() - stock.getOhlc().getClose()) / stock.getOhlc().getClose()) * 100;
                    totalPerformance += performanceScore;
                }
            }
            
            double avgPerformance = sectorStocks.isEmpty() ? 0 : totalPerformance / sectorStocks.size();
            double avgChangePercent = sectorStocks.isEmpty() ? 0 : totalChangePercent / sectorStocks.size();
            
            // Assign color based on performance
            String color = getColorForPerformance(avgPerformance);
            
            sectorPerformances.add(Heatmap.SectorPerformance.builder()
                .sectorName(sectorName)
                .performance(avgPerformance)
                .changePercent(avgChangePercent)
                .color(color)
                .build());
        }
        
        // Sort sectors by performance (highest to lowest)
        sectorPerformances.sort(Comparator.comparing(Heatmap.SectorPerformance::getPerformance).reversed());
        
        return Heatmap.builder()
            .indexSymbol(indexSymbol)
            .timestamp(Instant.now())
            .sectors(sectorPerformances)
            .build();
    }

        /**
     * Get color code based on performance value
     */
    private String getColorForPerformance(double performance) {
        if (performance > 3) return "#006400"; // Dark Green
        if (performance > 1) return "#32CD32"; // Lime Green
        if (performance > 0) return "#90EE90"; // Light Green
        if (performance > -1) return "#FFA07A"; // Light Salmon
        if (performance > -3) return "#FF4500"; // Orange Red
        return "#8B0000"; // Dark Red
    }
    
    /**
     * Get a mock sector name based on symbol prefix (for demonstration)
     * In a real implementation, this would come from a stock metadata service
     */
    private String getMockSectorForSymbol(String symbol) {
        // Simple mock implementation - in real code, get this from a proper source
        if (symbol.startsWith("INFO") || symbol.startsWith("TCS") || symbol.startsWith("WIPRO") || symbol.startsWith("INFY")) {
            return "Information Technology";
        } else if (symbol.startsWith("HDFC") || symbol.startsWith("ICICI") || symbol.startsWith("SBI") || symbol.startsWith("PNB")) {
            return "Financial Services";
        } else if (symbol.startsWith("RELIANCE") || symbol.startsWith("ONGC")) {
            return "Energy";
        } else if (symbol.startsWith("BHARTI") || symbol.startsWith("IDEA")) {
            return "Telecommunication";
        } else if (symbol.startsWith("ITC") || symbol.startsWith("HUL")) {
            return "Consumer Goods";
        } else {
            return "Others";
        }
    }
    

    
    /**
     * Get top gainers and losers for an index
     * @param indexSymbol The index symbol
     * @param limit Number of top gainers/losers to return
     * @return GainerLoser object containing top performers and underperformers
     */
    public GainerLoser getTopGainersLosers(String indexSymbol, int limit) {
        log.info("Getting top {} gainers and losers for index: {}", limit, indexSymbol);
        
        // This would use the stock indices data to find top gainers and losers
        // For now, returning a placeholder implementation
        List<GainerLoser.StockMovement> gainers = new ArrayList<>();
        List<GainerLoser.StockMovement> losers = new ArrayList<>();
        
        // In a real implementation, we would:
        // 1. Get all stocks in the index
        // 2. Calculate performance for each
        // 3. Sort by performance
        // 4. Take top/bottom N based on limit
        
        return GainerLoser.builder()
            .indexSymbol(indexSymbol)
            .timestamp(Instant.now())
            .topGainers(gainers)
            .topLosers(losers)
            .build();
    }
    
    /**
     * Calculate sector and industry allocation percentages for an index
     * @param indexSymbol The index symbol
     * @return SectorAllocation containing sector and industry weights
     */
    public SectorAllocation calculateSectorAllocations(String indexSymbol) {
        log.info("Calculating sector allocations for index: {}", indexSymbol);
        
        // This would use the stock indices data to calculate allocations
        // For now, returning a placeholder implementation
        List<SectorAllocation.SectorWeight> sectorWeights = new ArrayList<>();
        List<SectorAllocation.IndustryWeight> industryWeights = new ArrayList<>();
        
        // In a real implementation, we would:
        // 1. Get all stocks in the index
        // 2. Group them by sector and industry
        // 3. Calculate market cap and weight percentages
        // 4. Find top stocks in each sector/industry
        
        return SectorAllocation.builder()
            .indexSymbol(indexSymbol)
            .timestamp(Instant.now())
            .sectorWeights(sectorWeights)
            .industryWeights(industryWeights)
            .build();
    }
    
    /**
     * Calculate market capitalization allocation for an index
     * @param indexSymbol The index symbol
     * @return MarketCapAllocation containing breakdown by market cap segments
     */
    public MarketCapAllocation calculateMarketCapAllocations(String indexSymbol) {
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
        var marketData = marketDataService.getOhlcData(indexStockSymbols);
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
