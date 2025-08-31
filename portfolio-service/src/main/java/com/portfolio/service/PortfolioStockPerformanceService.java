package com.portfolio.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.am.common.amcommondata.model.asset.AssetModel;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.portfolio.marketdata.client.MarketDataApiClient;
import com.portfolio.marketdata.model.FilterType;
import com.portfolio.marketdata.model.InstrumentType;
import com.portfolio.marketdata.model.MarketDataLivePricesResponse;
import com.portfolio.marketdata.model.StockPrice;
import com.portfolio.model.PaginatedStockPerformance;
import com.portfolio.model.PerformanceStatistics;
import com.portfolio.model.StockPerformance;
import com.portfolio.model.StockPerformanceGroup;
import com.portfolio.model.cache.StockPriceCache;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.market.TimeFrame;
import com.portfolio.redis.service.StockIndicesRedisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioStockPerformanceService {
    private final StockIndicesRedisService stockPriceRedisService;
    private final MarketDataApiClient marketDataApiClient;
    private static final int DEFAULT_TOP_N = 5;
    private static final int DEFAULT_PAGE_SIZE = 5;

    /**
     * Calculates stock performances for a list of equity holdings, optionally over a time interval.
     * If no interval is provided, batch fetches live prices for all symbols at once.
     * If interval is provided, uses historical data for each symbol.
     *
     * @param equityHoldings List of equity holdings to calculate performance for
     * @param interval Optional time interval for historical performance calculation
     * @return List of stock performances
     */
    public List<StockPerformance> calculateStockPerformances(List<EquityModel> equityHoldings, TimeInterval interval) {
        // Handle empty input case
        if (equityHoldings == null || equityHoldings.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Determine if we need historical data or current prices
        // startTime will be null for OVERALL interval or intervals with null duration
        Instant startTime = calculateStartTimeFromInterval(interval);
        
        // Process based on whether we need historical or current data
        if (startTime == null) {
            // For OVERALL interval or null duration, use current prices
            return calculateCurrentPerformances(equityHoldings);
        } else {
            // For specific time intervals, use historical data
            return calculateHistoricalPerformances(equityHoldings, startTime);
        }
    }
    
    /**
     * Calculates the start time based on the provided interval
     * 
     * @param interval Time interval for calculation
     * @return Start time instant, or null if interval is OVERALL
     */
    private Instant calculateStartTimeFromInterval(TimeInterval interval) {
        // If interval is OVERALL or has null duration, return null to indicate no time filtering
        if (interval == TimeInterval.OVERALL || interval.getDuration() == null) {
            return null;
        }
        
        // Otherwise calculate the start time based on the interval duration
        return Instant.now().minus(interval.getDuration());
    }
    
    /**
     * Calculates current (non-historical) performances for equity holdings
     * 
     * @param equityHoldings List of equity holdings
     * @return List of stock performances
     */
    private List<StockPerformance> calculateCurrentPerformances(List<EquityModel> equityHoldings) {
        // Extract all symbols
        List<String> symbols = extractSymbolsFromEquities(equityHoldings);
        
        // Fetch live prices for all symbols (from cache and API if needed)
        Map<String, StockPriceCache> cachedPrices = fetchLivePrices(symbols);
        
        // Build performance list from cached prices
        return buildPerformanceListFromPriceCache(equityHoldings, cachedPrices);
    }

    public StockPerformanceGroup calculatePerformanceGroup(
            List<StockPerformance> performances, 
            Integer pageNumber, 
            Integer pageSize,
            boolean isGainers) {
        
        // Sort performances by gain/loss percentage
        List<StockPerformance> sortedPerformances = sortPerformancesByGainLoss(performances, isGainers);

        // Get top performers
        List<StockPerformance> topPerformers = getTopPerformers(sortedPerformances, DEFAULT_TOP_N);

        // Create paginated results
        PaginatedStockPerformance paginatedPerformers = getPaginatedPerformances(
            sortedPerformances, pageNumber, pageSize, isGainers);

        // Calculate performance statistics
        PerformanceStatistics stats = calculatePerformanceStatistics(sortedPerformances, isGainers);
        
        return StockPerformanceGroup.builder()
            .topPerformers(topPerformers)
            .allPerformers(paginatedPerformers)
            .averagePerformance(stats.getAveragePerformance())
            .medianPerformance(stats.getMedianPerformance())
            .bestPerformance(stats.getBestPerformance())
            .worstPerformance(stats.getWorstPerformance())
            .totalCount(sortedPerformances.size())
            .build();
    }

    public double calculateCurrentValue(List<StockPerformance> performances) {
        return performances.stream()
            .mapToDouble(p -> p.getCurrentPrice() * p.getQuantity())
            .sum();
    }

    public double calculateHistoricalValue(List<StockPerformance> performances, Instant startTime) {
        return performances.stream()
            .mapToDouble(performance -> {
                List<StockPriceCache> historicalPrices = stockPriceRedisService.getHistoricalPrices(
                    performance.getSymbol(),
                    startTime.atZone(java.time.ZoneOffset.UTC).toLocalDateTime(),
                    Instant.now().atZone(java.time.ZoneOffset.UTC).toLocalDateTime()
                );
                
                if (!historicalPrices.isEmpty()) {
                    double historicalPrice = historicalPrices.get(0).getClosePrice();
                    return historicalPrice * performance.getQuantity();
                }
                return 0.0;
            })
            .sum();
    }

    private StockPerformance getGainLossPercentage(AssetModel asset, Instant startTime) {
        List<StockPriceCache> prices = null;
        
        // Part 1: Check cache for stock prices
        prices = checkCacheForPrices(asset.getSymbol(), startTime);
        
        // If cache miss, fetch from API based on startTime
        if (prices == null || prices.isEmpty()) {
            if (startTime == null) {
                // Part 2: Call live prices API if startTime is not present
                prices = fetchLivePricesFromApi(asset.getSymbol());
            } else {
                // Part 3: Call historical prices API if startTime is present
                prices = fetchHistoricalPricesFromApi(List.of(asset.getSymbol()), startTime);
            }
            
            // If still no prices, return null
            if (prices == null || prices.isEmpty()) {
                log.warn("No price data found for symbol: {} after checking cache and API", asset.getSymbol());
                return null;
            }
        }
        
        // Part 4: Calculate performance
        return calculatePerformance(asset, prices);
    }
    
    /**
     * Check the cache for stock prices
     * @param symbol Stock symbol
     * @param startTime Start time for historical data, null for latest price
     * @return List of stock prices from cache, or empty list if not found
     */
    private List<StockPriceCache> checkCacheForPrices(String symbol, Instant startTime) {
        try {
            if (startTime != null) {
                // Check cache for historical prices
                List<StockPriceCache> historicalPrices = stockPriceRedisService.getHistoricalPrices(
                    symbol,
                    startTime.atZone(java.time.ZoneOffset.UTC).toLocalDateTime(),
                    LocalDateTime.now()
                );
                
                if (!historicalPrices.isEmpty()) {
                    log.debug("Found historical prices in cache for symbol: {}", symbol);
                    return historicalPrices;
                }
                log.debug("No historical prices found in cache for symbol: {}", symbol);
            } else {
                // Check cache for latest price
                var latestPrice = stockPriceRedisService.getLatestPrice(symbol);
                if (latestPrice.isPresent()) {
                    log.debug("Found latest price in cache for symbol: {}", symbol);
                    return List.of(latestPrice.get());
                }
                log.debug("No latest price found in cache for symbol: {}", symbol);
            }
        } catch (Exception e) {
            log.error("Error checking cache for symbol: {}", symbol, e);
        }
        
        return List.of(); // Return empty list if not found or error
    }
    
    /**
     * Fetch live prices from the API
     * @param symbol Stock symbol
     * @return List of stock prices, or empty list if not found
     */
    private List<StockPriceCache> fetchLivePricesFromApi(String symbol) {
        // Check for null or empty symbol
        if (symbol == null || symbol.isEmpty()) {
            log.warn("Cannot fetch live prices for null or empty symbol");
            return List.of();
        }
        try {
            log.debug("Fetching live prices from API for symbol: {}", symbol);
            MarketDataLivePricesResponse response = marketDataApiClient.getLivePricesSync(List.of(symbol));
            
            if (response != null && response.getPrices() != null && !response.getPrices().isEmpty()) {
                // Convert API response to StockPriceCache
                StockPrice price = response.getPrices().stream()
                    .filter(p -> symbol.equals(p.getSymbol()))
                    .findFirst()
                    .orElse(null);
                
                if (price != null) {
                    StockPriceCache cachePrice = StockPriceCache.builder()
                        .symbol(price.getSymbol())
                        .closePrice(price.getClose())
                        .timestamp(Instant.now())
                        .build();
                    
                    log.debug("Successfully fetched live price from API for symbol: {}", symbol);
                    return List.of(cachePrice);
                }
            }
            
            log.warn("No live prices found in API for symbol: {}", symbol);
        } catch (Exception e) {
            log.error("Error fetching live prices from API for symbol: {}", symbol, e);
        }
        
        return List.of(); // Return empty list if not found or error
    }
    
    /**
     * Fetch historical prices from the API
     * @param symbol Stock symbol
     * @param startTime Start time for historical data
     * @return List of stock prices, or empty list if not found
     */
    private List<StockPriceCache> fetchHistoricalPricesFromApi(List<String> symbols, Instant startTime) {
        // Check for null or empty symbols
        if (symbols == null || symbols.isEmpty()) {
            log.warn("Cannot fetch historical prices for null or empty symbols");
            return List.of();
        }
        try {
            log.debug("Fetching historical prices from API for symbols: {} since {}", symbols, startTime);
            
            // Convert startTime to LocalDate for API call
            java.time.LocalDate fromDate = startTime.atZone(java.time.ZoneOffset.UTC).toLocalDate();
            java.time.LocalDate toDate = java.time.LocalDate.now();
            
            var response = marketDataApiClient.getHistoricalDataSync(
                symbols,
                fromDate,
                toDate,
                TimeFrame.DAY,
                InstrumentType.EQ,
                FilterType.ALL
            );
            
            // Check if response is valid
            if (response == null || response.getData() == null) {
                log.warn("Received null response or data map from historical data API");
                return List.of();
            }
            
            // Process each symbol in the response
            List<StockPriceCache> results = new ArrayList<>();
            
            for (String symbol : symbols) {
                if (response.getData().containsKey(symbol)) {
                    // Get the historical data for this symbol
                    var symbolResponse = response.getData().get(symbol);
                    
                    if (symbolResponse != null && symbolResponse.getData() != null && 
                        symbolResponse.getData().getDataPoints() != null && 
                        !symbolResponse.getData().getDataPoints().isEmpty()) {
                    
                        // Get the list of data points
                        var dataPoints = symbolResponse.getData().getDataPoints();
                        
                        // Get the most recent data point
                        var latestDataPoint = dataPoints.get(dataPoints.size() - 1);
                        
                        // Create a StockPriceCache from the historical data point
                        StockPriceCache cachePrice = StockPriceCache.builder()
                            .symbol(symbol)
                            .closePrice(latestDataPoint.getClose())
                            .timestamp(Instant.now()) // Use current time as fallback since we can't access timestamp directly
                            .build();
                        
                        log.debug("Successfully fetched historical price from API for symbol: {}", symbol);
                        results.add(cachePrice);
                    } else {
                        log.warn("Invalid or empty data structure for symbol: {}", symbol);
                    }
                } else {
                    log.warn("No data found for symbol: {} in response", symbol);
                }
            }
            
            if (results.isEmpty()) {
                log.warn("No historical prices found in API for any of the requested symbols");
            } else {
                log.info("Successfully fetched historical prices for {} out of {} symbols", results.size(), symbols.size());
            }
            
            return results;
        } catch (Exception e) {
            log.error("Error fetching historical prices from API for symbols: {}", symbols, e);
        }
        
        return List.of(); // Return empty list if not found or error
    }
    
    /**
     * Fetches live prices for a list of symbols, checking cache first and then API for missing symbols.
     *
     * @param symbols List of stock symbols to fetch prices for
     * @return Map of symbols to their price cache objects
     */
    private Map<String, StockPriceCache> fetchLivePrices(List<String> symbols) {
        Map<String, StockPriceCache> cachedPrices = new HashMap<>();
        List<String> symbolsNotInCache = new ArrayList<>();
        
        // Check which symbols are in cache
        for (String symbol : symbols) {
            var latestPrice = stockPriceRedisService.getLatestPrice(symbol);
            if (latestPrice.isPresent()) {
                cachedPrices.put(symbol, latestPrice.get());
            } else {
                symbolsNotInCache.add(symbol);
            }
        }
        
        // If there are symbols not in cache, fetch them in batch using existing API method
        if (!symbolsNotInCache.isEmpty()) {
            log.debug("Batch fetching live prices for {} symbols not found in cache", symbolsNotInCache.size());
            // Use the existing API client method that accepts a list of symbols
            MarketDataLivePricesResponse livePricesResponse = marketDataApiClient.getLivePricesSync(symbolsNotInCache);
            
            // Process the response and add to cache
            if (livePricesResponse != null && livePricesResponse.getPrices() != null) {
                for (StockPrice price : livePricesResponse.getPrices()) {
                    StockPriceCache cachePrice = StockPriceCache.builder()
                        .symbol(price.getSymbol())
                        .closePrice(price.getClose())
                        .timestamp(Instant.now())
                        .build();
                    
                    cachedPrices.put(price.getSymbol(), cachePrice);
                }
            }
        }
        
        return cachedPrices;
    }
    
    /**
     * Calculate performance metrics based on stock prices
     * @param asset Asset model containing symbol, quantity, and average price
     * @param prices List of stock prices
     * @return StockPerformance object with calculated metrics
     */
    private StockPerformance calculatePerformance(AssetModel asset, List<StockPriceCache> prices) {
        if (prices == null || prices.isEmpty()) {
            return null;
        }
        double currentPrice = prices.get(prices.size() - 1).getClosePrice();
        return calculatePerformanceFromPrice(asset, currentPrice);
    }
    
    /**
     * Calculate performance metrics directly from a price value
     * @param asset Asset model containing symbol, quantity, and average price
     * @param currentPrice Current price of the asset
     * @return StockPerformance object with calculated metrics
     */
    private StockPerformance calculatePerformanceFromPrice(AssetModel asset, double currentPrice) {
        double averagePrice = asset.getAvgBuyingPrice();
        double quantity = asset.getQuantity();
        double gainLoss = (currentPrice - averagePrice) * quantity;
        double gainLossPercentage = ((currentPrice - averagePrice) / averagePrice) * 100;

        return StockPerformance.builder()
            .symbol(asset.getSymbol())
            .quantity(quantity)
            .currentPrice(currentPrice)
            .averagePrice(averagePrice)
            .gainLoss(gainLoss)
            .gainLossPercentage(gainLossPercentage)
            .build();
    }

    /**
     * Builds a list of stock performances from equity holdings and price cache
     * 
     * @param equityHoldings List of equity holdings
     * @param priceCache Map of symbols to their price cache objects
     * @return List of stock performances
     */
    private List<StockPerformance> buildPerformanceListFromPriceCache(
            List<EquityModel> equityHoldings, 
            Map<String, StockPriceCache> priceCache) {
        
        List<StockPerformance> performances = new ArrayList<>();
        for (EquityModel asset : equityHoldings) {
            StockPriceCache cachedPrice = priceCache.get(asset.getSymbol());
            
            if (cachedPrice != null) {
                // Calculate performance using the extracted method
                performances.add(calculatePerformanceFromPrice(asset, cachedPrice.getClosePrice()));
            } else {
                log.warn("No price data found for symbol: {} after checking cache and API", asset.getSymbol());
            }
        }
        
        return performances;
    }
    
    /**
     * Calculates historical performances for a list of equity holdings
     * 
     * @param equityHoldings List of equity holdings
     * @param startTime Start time for historical data
     * @return List of stock performances
     */
    private List<StockPerformance> calculateHistoricalPerformances(List<EquityModel> equityHoldings, Instant startTime) {
        List<String> symbols = equityHoldings.stream().map(EquityModel::getSymbol).toList();
        List<StockPriceCache> prices = fetchHistoricalPricesFromApi(symbols, startTime);
    }

    /**
     * Sorts performances by gain/loss percentage
     * 
     * @param performances List of performances to sort
     * @param isGainers Whether to sort for gainers (true) or losers (false)
     * @return Sorted list of performances
     */
    private List<StockPerformance> sortPerformancesByGainLoss(
            List<StockPerformance> performances, 
            boolean isGainers) {
        
        List<StockPerformance> sortedPerformances = new ArrayList<>(performances);
        sortedPerformances.sort(Comparator.comparing(StockPerformance::getGainLossPercentage));
        
        if (isGainers) {
            Collections.reverse(sortedPerformances);
        }
        
        return sortedPerformances;
    }
    
    /**
     * Gets the top N performers from a sorted list
     * 
     * @param sortedPerformances Already sorted list of performances
     * @param count Number of top performers to return
     * @return List of top performers
     */
    private List<StockPerformance> getTopPerformers(List<StockPerformance> sortedPerformances, int count) {
        return sortedPerformances.subList(0, Math.min(count, sortedPerformances.size()));
    }
    
    /**
     * Calculates performance statistics from a list of sorted performances
     * 
     * @param sortedPerformances Already sorted list of performances
     * @param isGainers Whether the list is sorted for gainers (true) or losers (false)
     * @return PerformanceStatistics object containing calculated statistics
     */
    private PerformanceStatistics calculatePerformanceStatistics(
            List<StockPerformance> sortedPerformances, 
            boolean isGainers) {
            
        double[] percentages = sortedPerformances.stream()
            .mapToDouble(StockPerformance::getGainLossPercentage)
            .toArray();
            
        return PerformanceStatistics.fromPercentages(percentages, isGainers);
    }
    
    // PerformanceStatistics moved to com.portfolio.model.PerformanceStatistics
    
    private List<String> extractSymbolsFromEquities(List<EquityModel> equities) {
        return equities.stream()
            .map(EquityModel::getSymbol)
            .filter(symbol -> symbol != null && !symbol.isEmpty())
            .collect(Collectors.toList());
    }

    private PaginatedStockPerformance getPaginatedPerformances(
            List<StockPerformance> performances, 
            Integer pageNumber, 
            Integer pageSize,
            boolean isGainers) {
        
        int actualPageNumber = pageNumber != null ? pageNumber : 0;
        int actualPageSize = pageSize != null ? pageSize : DEFAULT_PAGE_SIZE;
        
        List<StockPerformance> sortedPerformances = new ArrayList<>(performances);
        if (isGainers) {
            java.util.Collections.reverse(sortedPerformances);
        }

        int totalElements = sortedPerformances.size();
        int totalPages = (int) Math.ceil((double) totalElements / actualPageSize);
        
        int start = actualPageNumber * actualPageSize;
        int end = Math.min(start + actualPageSize, totalElements);
        
        if (start >= totalElements) {
            return PaginatedStockPerformance.builder()
                .content(List.of())
                .pageNumber(actualPageNumber)
                .pageSize(actualPageSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .isLastPage(true)
                .build();
        }

        List<StockPerformance> pageContent = sortedPerformances.subList(start, end);
        
        return PaginatedStockPerformance.builder()
            .content(pageContent)
            .pageNumber(actualPageNumber)
            .pageSize(actualPageSize)
            .totalElements(totalElements)
            .totalPages(totalPages)
            .isLastPage(end >= totalElements)
            .build();
    }
    
    // calculateAverage and calculateMedian methods moved to PerformanceStatistics model class
}
