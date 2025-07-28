package com.portfolio.analytics.service.providers;

import com.am.common.amcommondata.model.MarketCapType;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.analytics.service.AbstractPortfolioAnalyticsProvider;
import com.portfolio.analytics.service.AnalyticsType;
import com.portfolio.analytics.service.utils.SecurityDetailsService;
import com.portfolio.marketdata.model.MarketDataResponse;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.analytics.MarketCapAllocation;
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
        
        // Get portfolio data
        PortfolioModelV1 portfolio = getPortfolio(portfolioId);
        if (portfolio == null || portfolio.getEquityModels() == null || portfolio.getEquityModels().isEmpty()) {
            log.warn("No portfolio or holdings found for ID: {}", portfolioId);
            return MarketCapAllocation.builder()
                .portfolioId(portfolioId)
                .timestamp(Instant.now())
                .segments(Collections.emptyList())
                .build();
        }
        
        // Get symbols from portfolio holdings
        List<String> portfolioSymbols = getPortfolioSymbols(portfolio);
        if (portfolioSymbols.isEmpty()) {
            log.warn("No stock symbols found in portfolio: {}", portfolioId);
            return MarketCapAllocation.builder()
                .portfolioId(portfolioId)
                .timestamp(Instant.now())
                .segments(Collections.emptyList())
                .build();
        }
        
        // Fetch market data for all stocks in the portfolio
        Map<String, MarketDataResponse> marketData = getMarketData(portfolioSymbols);
        if (marketData.isEmpty()) {
            log.warn("No market data available for portfolio: {}", portfolioId);
            return MarketCapAllocation.builder()
                .portfolioId(portfolioId)
                .timestamp(Instant.now())
                .segments(Collections.emptyList())
                .build();
        }
        
        // Use SecurityDetailsService to group symbols by market cap type
        Map<String, List<String>> marketCapGroups = securityDetailsService.groupSymbolsByMarketType(portfolioSymbols);
        
        log.info("Market cap groups for portfolio {}: {}", portfolioId, marketCapGroups.keySet());
        
        // Create a mapping for market cap type enum to segment name
        Map<String, String> marketCapTypeToSegmentName = new HashMap<>();
        marketCapTypeToSegmentName.put(MarketCapType.LARGE_CAP.name(), "Large Cap");
        marketCapTypeToSegmentName.put(MarketCapType.MID_CAP.name(), "Mid Cap");
        marketCapTypeToSegmentName.put(MarketCapType.SMALL_CAP.name(), "Small Cap");
        marketCapTypeToSegmentName.put(MarketCapType.MICRO_CAP.name(), "Micro Cap");
        marketCapTypeToSegmentName.put("null", "Unknown"); // Handle null market cap type
        
        // Create a map of symbol to holding quantity
        Map<String, Double> symbolToQuantity = portfolio.getEquityModels().stream()
            .collect(Collectors.toMap(
                EquityModel::getSymbol,
                EquityModel::getQuantity,
                (a, b) -> a + b // In case of duplicate symbols, sum the quantities
            ));
        
        // Calculate market cap for each stock
        double totalPortfolioValue = 0.0;
        Map<String, Double> stockMarketValues = new HashMap<>();
        Map<String, String> symbolToSegment = new HashMap<>(); // Map to store symbol to segment mapping
        
        for (String symbol : marketData.keySet()) {
            MarketDataResponse data = marketData.get(symbol);
            double quantity = symbolToQuantity.getOrDefault(symbol, 0.0);
            
            // Calculate market value of this holding
            double marketValue = data.getLastPrice() * quantity;
            stockMarketValues.put(symbol, marketValue);
            totalPortfolioValue += marketValue;
            
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
                // Use the market cap from security details or estimate it
                double marketCap = data.getLastPrice() * 1000000000.0; // Assuming 1B shares for estimation
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
        Map<String, List<String>> segmentToSymbols = new HashMap<>();
        for (Map.Entry<String, String> entry : symbolToSegment.entrySet()) {
            String symbol = entry.getKey();
            String segment = entry.getValue();
            segmentToSymbols.computeIfAbsent(segment, k -> new ArrayList<>()).add(symbol);
        }
        
        // Calculate allocation percentages and create segment objects
        List<MarketCapAllocation.CapSegment> segments = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : segmentToSymbols.entrySet()) {
            String segmentName = entry.getKey();
            List<String> segmentSymbols = entry.getValue();
            
            if (segmentSymbols.isEmpty()) {
                continue; // Skip empty segments
            }
            
            // Calculate total market value for this segment
            double segmentMarketValue = 0.0;
            Map<String, Double> symbolToMarketValue = new HashMap<>();
            
            for (String symbol : segmentSymbols) {
                double marketValue = stockMarketValues.getOrDefault(symbol, 0.0);
                segmentMarketValue += marketValue;
                symbolToMarketValue.put(symbol, marketValue);
            }
            
            // Calculate weight percentage
            double weightPercentage = totalPortfolioValue > 0 ? (segmentMarketValue / totalPortfolioValue) * 100 : 0;
            
            // Get top stocks by market value for this segment
            List<String> topStocks = symbolToMarketValue.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5)  // Top 5 stocks
                .map(Map.Entry::getKey)
                .toList();
            
            segments.add(MarketCapAllocation.CapSegment.builder()
                .segmentName(segmentName)
                .weightPercentage(weightPercentage)
                .totalMarketCap(segmentMarketValue)  // This is actually market value, not market cap
                .numberOfStocks(segmentSymbols.size())
                .topStocks(topStocks)
                .build());
        }
        
        // Sort segments by weight percentage (highest to lowest)
        segments.sort(Comparator.comparing(MarketCapAllocation.CapSegment::getWeightPercentage).reversed());
        
        return MarketCapAllocation.builder()
            .portfolioId(portfolioId)
            .timestamp(Instant.now())
            .segments(segments)
            .build();
    }
}
