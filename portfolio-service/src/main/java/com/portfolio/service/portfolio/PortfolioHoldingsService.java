package com.portfolio.service.portfolio;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.mapper.holdings.PortfolioHoldingsMapper;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.model.portfolio.PortfolioHoldings;
import com.portfolio.redis.service.PortfolioHoldingsRedisService;
import com.portfolio.redis.service.StockIndicesRedisService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.market.MarketData;

import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioHoldingsService {
    
    private final PortfolioService portfolioService;
    private final PortfolioHoldingsMapper portfolioHoldingsMapper;
    private final StockIndicesRedisService stockPriceRedisService;
    private final PortfolioHoldingsRedisService portfolioHoldingsRedisService;
    private final MarketDataService marketDataService;

    public PortfolioHoldings getPortfolioHoldings(String userId, TimeInterval interval) {
        log.info("Starting getPortfolioHoldings - User: {}, Interval: {}", userId, interval != null ? interval.getCode() : "null");
        
        Optional<PortfolioHoldings> cachedHoldings = getCachedHoldings(userId, interval);
        if (cachedHoldings.isPresent()) {
            log.info("Returning cached portfolio holdings for user: {}", userId);
            return cachedHoldings.get();
        }
        
        log.info("Cache miss for portfolio holdings - User: {}, fetching from source", userId);
        var portfolios = portfolioService.getPortfoliosByUserId(userId);
        if (portfolios == null || portfolios.isEmpty()) {
            log.warn("No portfolios found for user: {}", userId);
            return null;
        }
        log.info("Found {} portfolios for user: {}", portfolios.size(), userId);
        
        var portfolioHoldings = buildPortfolioHoldings(portfolios, userId, null, interval);

        log.info("Completed getPortfolioHoldings for user: {}", userId);
        return portfolioHoldings;
    }

    /**
     * Retrieves the portfolio holdings for a specific portfolio of the given user and time interval.
     * 
     * @param userId      the ID of the user
     * @param portfolioId the ID of the specific portfolio to filter by
     * @param interval    the time interval
     * @return the portfolio holdings for the specific portfolio
     */
    public PortfolioHoldings getPortfolioHoldings(String userId, String portfolioId, TimeInterval interval) {
        log.info("Starting getPortfolioHoldings for specific portfolio - User: {}, Portfolio: {}, Interval: {}", 
            userId, portfolioId, interval != null ? interval.getCode() : "null");
        
        // For specific portfolio, we don't use cache as it's more targeted
        var portfolios = portfolioService.getPortfoliosByUserId(userId);
        if (portfolios == null || portfolios.isEmpty()) {
            log.warn("No portfolios found for user: {}", userId);
            return null;
        }
        log.info("Found {} portfolios for user: {}", portfolios.size(), userId);
        
        // Filter for the specific portfolio
        var filteredPortfolios = portfolios.stream()
            .filter(portfolio -> portfolio.getId() != null && portfolio.getId().toString().equals(portfolioId))
            .collect(java.util.stream.Collectors.toList());
            
        if (filteredPortfolios.isEmpty()) {
            log.warn("No portfolio found with ID: {} for user: {}", portfolioId, userId);
            return null;
        }
        
        log.info("Found {} matching portfolio(s) for ID: {} and user: {}", 
            filteredPortfolios.size(), portfolioId, userId);
        
        var portfolioHoldings = buildPortfolioHoldings(filteredPortfolios, userId, portfolioId, interval);
        
         // Note: We do not cache specific portfolio holdings to avoid stale data issues
        
        log.info("Completed getPortfolioHoldings for user: {} and portfolio: {}", userId, portfolioId);
        return portfolioHoldings;
    }

    /**
     * Builds portfolio holdings from filtered portfolios with enrichment
     * 
     * @param portfolios  the list of portfolios to process
     * @param userId      the user ID for logging
     * @param portfolioId the portfolio ID for logging (null if processing all portfolios)
     * @return the complete portfolio holdings with enriched data
     */
    private PortfolioHoldings buildPortfolioHoldings(List<PortfolioModelV1> portfolios, String userId, String portfolioId, TimeInterval interval) {
        String context = portfolioId != null ? "portfolio: " + portfolioId : "all portfolios";
        log.debug("Building portfolio holdings for user: {} and {}", userId, context);
        
        var portfolioHoldings = portfolioHoldingsMapper.toPortfolioHoldingsV1(portfolios);
        
        log.info("Enriching stock prices and performance data for {} equity holdings for {}", 
            portfolioHoldings.getEquityHoldings() != null ? portfolioHoldings.getEquityHoldings().size() : 0, context);
        portfolioHoldings.setEquityHoldings(enrichStockPriceAndPerformance(portfolioHoldings.getEquityHoldings()));
        portfolioHoldings.setLastUpdated(LocalDateTime.now());
        
        log.debug("Completed building portfolio holdings for user: {} and {}", userId, context);

         // Store in cache only for all portfolios (not for specific portfolio)
        log.info("Caching portfolio holdings for user: {}", userId);
        portfolioHoldingsRedisService.cachePortfolioHoldings(portfolioHoldings, userId, interval);
        
        log.info("Completed getPortfolioHoldings for user: {}", userId);
        return portfolioHoldings;
    }

    private PortfolioHoldings getPortfolioHoldings(List<PortfolioModelV1> portfolios) {
        
        var portfolioHoldings = portfolioHoldingsMapper.toPortfolioHoldingsV1(portfolios);
        
        log.info("Enriching stock prices and performance data for {} equity holdings", 
        portfolioHoldings.getEquityHoldings() != null ? portfolioHoldings.getEquityHoldings().size() : 0);
        portfolioHoldings.setEquityHoldings(enrichStockPriceAndPerformance(portfolioHoldings.getEquityHoldings()));
        portfolioHoldings.setLastUpdated(LocalDateTime.now());
        return portfolioHoldings;
    }

    protected List<EquityHoldings> getHoldings(List<PortfolioModelV1> portfolios) {
        
        var equityHoldings = portfolioHoldingsMapper.toEquityHoldings(portfolios);
        
        log.info("Enriching stock prices and performance data for {} equity holdings", 
            equityHoldings != null ? equityHoldings.size() : 0);
        equityHoldings = enrichStockPriceAndPerformance(equityHoldings);
        return equityHoldings;
    }


    private List<EquityHoldings> enrichStockPriceAndPerformance(List<EquityHoldings> equityHoldings) {
        log.debug("Enriching {} equity holdings with price and performance data", 
            equityHoldings != null ? equityHoldings.size() : 0);
        
        if (equityHoldings == null || equityHoldings.isEmpty()) {
            log.debug("No equity holdings to enrich");
            return equityHoldings;
        }
        
        // Extract all symbols from equity holdings
        List<String> symbols = equityHoldings.stream()
            .map(EquityHoldings::getSymbol)
            .filter(symbol -> symbol != null)
            .collect(Collectors.toList());
            
        // Fetch market data for all symbols in a single call
        Map<String, MarketData> marketDataMap = marketDataService.getMarketData(symbols);
        log.debug("Fetched market data for {} out of {} symbols", marketDataMap.size(), symbols.size());
        
        // First pass: enrich each holding with market data
        List<EquityHoldings> enrichedHoldings = equityHoldings.stream()
            .map(holding -> enrichStockPriceAndPerformance(holding, marketDataMap))
            .toList();
            
        // Calculate total portfolio value for weight calculation
        Double totalPortfolioValue = enrichedHoldings.stream()
            .filter(h -> h.getCurrentValue() != null)
            .mapToDouble(EquityHoldings::getCurrentValue)
            .sum();
            
        log.debug("Total portfolio value for weight calculation: {}", totalPortfolioValue);
        
        // Second pass: calculate weight in portfolio
        if (totalPortfolioValue > 0) {
            enrichedHoldings.forEach(holding -> {
                if (holding.getCurrentValue() != null) {
                    double weight = (holding.getCurrentValue() / totalPortfolioValue) * 100.0;
                    holding.setWeightInPortfolio(roundToTwoDecimalPlaces(weight));
                    log.debug("Set weight for {} to {}%", holding.getSymbol(), holding.getWeightInPortfolio());
                }
            });
        }
        
        return enrichedHoldings;
    }

    private EquityHoldings enrichStockPriceAndPerformance(EquityHoldings equityHoldings, Map<String, MarketData> marketDataMap) {
        String symbol = equityHoldings.getSymbol();
        
        if (symbol == null) {
            log.warn("Equity holding has no symbol, cannot enrich with market data");
            return equityHoldings;
        }
        
        log.debug("Enriching equity holding: Symbol={}, Quantity={}", 
            symbol, equityHoldings.getQuantity());
        
        // Get market data for this symbol
        MarketData marketData = marketDataMap.get(symbol);
        
        if (marketData != null) {
            // Determine current price from market data
            Double currentPrice = null;
            Double previousClosePrice = null;
            
            // First check lastPrice (current price) and use it if available
            if (marketData.getLastPrice() != null) {
                currentPrice = marketData.getLastPrice();
                log.debug("Using lastPrice for {}: {}", symbol, currentPrice);
            } 
            // Fall back to OHLC close price if lastPrice is not available
            else if (marketData.getOhlc() != null) {
                currentPrice = marketData.getOhlc().getClose();
                log.debug("Using OHLC close price for {}: {}", symbol, currentPrice);
            }
            
            // Get previous close price for today's gain/loss calculation
            if (marketData.getOhlc() != null) {
                // For today's calculation, use the open price as reference
                previousClosePrice = marketData.getOhlc().getOpen();
                
                // Calculate stock price percentage change
                if (previousClosePrice > 0 && currentPrice != null) {
                    Double priceChange = currentPrice - previousClosePrice;
                    Double percentageChange = (priceChange / previousClosePrice) * 100;
                    equityHoldings.setPercentageChange(roundToTwoDecimalPlaces(percentageChange));
                    log.debug("Calculated stock price change for {}: {}% (from {} to {})", 
                        symbol, equityHoldings.getPercentageChange(), previousClosePrice, currentPrice);
                }
            }
            
            if (currentPrice != null && equityHoldings.getQuantity() != null) {
                Double currentValue = currentPrice * equityHoldings.getQuantity();
                
                log.debug("Price data found for {}: Price={}, Value={}", 
                    symbol, currentPrice, currentValue);
                    
                // Update equity holding with current price and value
                equityHoldings.setCurrentPrice(roundToTwoDecimalPlaces(currentPrice));
                equityHoldings.setCurrentValue(roundToTwoDecimalPlaces(currentValue));
                
                // Calculate overall gain/loss metrics if investment cost is available
                if (equityHoldings.getInvestmentCost() != null && equityHoldings.getInvestmentCost() > 0) {
                    Double gainLoss = currentValue - equityHoldings.getInvestmentCost();
                    Double gainLossPercentage = (gainLoss / equityHoldings.getInvestmentCost()) * 100;
                    
                    equityHoldings.setGainLoss(roundToTwoDecimalPlaces(gainLoss));
                    equityHoldings.setGainLossPercentage(roundToTwoDecimalPlaces(gainLossPercentage));
                    
                    log.debug("Calculated overall performance for {}: GainLoss={}, GainLossPercentage={}%", 
                        symbol, gainLoss, gainLossPercentage);
                }
                
                // Calculate today's gain/loss metrics if previous close price is available
                if (previousClosePrice != null && previousClosePrice > 0) {
                    Double previousValue = previousClosePrice * equityHoldings.getQuantity();
                    Double todayGainLoss = currentValue - previousValue;
                    Double todayGainLossPercentage = (todayGainLoss / previousValue) * 100;
                    
                    equityHoldings.setTodayGainLoss(roundToTwoDecimalPlaces(todayGainLoss));
                    equityHoldings.setTodayGainLossPercentage(roundToTwoDecimalPlaces(todayGainLossPercentage));
                    
                    log.debug("Calculated today's performance for {}: TodayGainLoss={}, TodayGainLossPercentage={}%", 
                        symbol, todayGainLoss, todayGainLossPercentage);
                }
            }
        } else {
            // Fall back to Redis if market data is not available
            log.debug("No market data found for {}, falling back to Redis", symbol);
            var latestPrice = stockPriceRedisService.getLatestPrice(symbol);
            
            if (latestPrice.isPresent()) {
                var currentPrice = latestPrice.get().getClosePrice();
                var currentValue = currentPrice * equityHoldings.getQuantity();
                
                log.debug("Redis price data found for {}: Price={}, Value={}", 
                    symbol, currentPrice, currentValue);
                    
                equityHoldings.setCurrentPrice(roundToTwoDecimalPlaces(currentPrice));
                equityHoldings.setCurrentValue(roundToTwoDecimalPlaces(currentValue));
                
                if (equityHoldings.getInvestmentCost() != null && equityHoldings.getInvestmentCost() > 0) {
                    Double gainLoss = currentValue - equityHoldings.getInvestmentCost();
                    Double gainLossPercentage = (gainLoss / equityHoldings.getInvestmentCost()) * 100;
                    
                    equityHoldings.setGainLoss(roundToTwoDecimalPlaces(gainLoss));
                    equityHoldings.setGainLossPercentage(roundToTwoDecimalPlaces(gainLossPercentage));
                }
                
                // Note: We can't calculate today's gain/loss from Redis data as we don't have previous close price
            } else {
                log.warn("No price data found for symbol: {} in either market data or Redis", symbol);
            }
        }
        
        return equityHoldings;
    }

    /**
     * Utility method to round a double value to two decimal places
     * 
     * @param value The value to round
     * @return The rounded value
     */
    private Double roundToTwoDecimalPlaces(Double value) {
        if (value == null) {
            return null;
        }
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
    
    private Optional<PortfolioHoldings> getCachedHoldings(String userId, TimeInterval interval) {
        log.debug("Checking cache for portfolio holdings - User: {}, Interval: {}", 
            userId, interval != null ? interval.getCode() : "null");
            
        Optional<PortfolioHoldings> cachedHoldings = portfolioHoldingsRedisService.getLatestHoldings(userId, interval);
        if (cachedHoldings.isPresent()) {
            log.info("Serving portfolio holdings from cache - User: {}, Interval: {}", 
                userId, interval != null ? interval.getCode() : "null");
        }
        return cachedHoldings;
    }
}
