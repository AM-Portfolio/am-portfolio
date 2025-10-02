package com.portfolio.service.portfolio;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.enums.BrokerType;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.kafka.mapper.PortfolioMapperv1;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.model.portfolio.v1.BrokerPortfolioSummary;
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;
import com.portfolio.redis.service.PortfolioSummaryRedisService;
import com.portfolio.marketdata.service.MarketDataService;
import com.portfolio.model.market.MarketData;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioOverviewService {
    
    private final PortfolioService portfolioService;
    private final PortfolioHoldingsService portfolioHoldingsService;
    private final PortfolioMapperv1 portfolioMapper;
    private final PortfolioSummaryRedisService portfolioSummaryRedisService;
    private final MarketDataService marketDataService;

    public PortfolioSummaryV1 overviewPortfolio(String userId, TimeInterval interval) {
        log.info("Starting overviewPortfolio - User: {}, Interval: {}", 
            userId, interval != null ? interval.getCode() : "null");
            
        Optional<PortfolioSummaryV1> cachedSummary = getCachedSummary(userId, interval);
        if (cachedSummary.isPresent()) {
            log.info("Returning cached portfolio summary for user: {}", userId);
            return cachedSummary.get();
        }

        log.info("Cache miss for portfolio summary - User: {}, fetching from source", userId);
        var portfolios = portfolioService.getPortfoliosByUserId(userId);
        log.info("Retrieved {} portfolios for user: {}", 
            portfolios != null ? portfolios.size() : 0, userId);
            
        if (portfolios == null || portfolios.isEmpty()) {
            log.warn("No portfolios found for user: {}", userId);
            return null;
        }

        PortfolioSummaryV1 finalSummary = buildPortfolioSummary(portfolios, userId, null, interval);
        log.info("Completed overviewPortfolio for user: {}", userId);
        return finalSummary;
    }

    /**
     * Provides an overview of a specific portfolio for the given user, portfolio ID and time interval.
     * 
     * @param userId      the ID of the user
     * @param portfolioId the ID of the specific portfolio to filter by
     * @param interval    the time interval
     * @return the portfolio summary for the specific portfolio
     */
    public PortfolioSummaryV1 overviewPortfolio(String userId, String portfolioId, TimeInterval interval) {
        log.info("Starting overviewPortfolio for specific portfolio - User: {}, Portfolio: {}, Interval: {}", 
            userId, portfolioId, interval != null ? interval.getCode() : "null");
            
        // For specific portfolio, we don't use cache as it's more targeted
        var portfolios = portfolioService.getPortfoliosByUserId(userId);
        log.info("Retrieved {} portfolios for user: {}", 
            portfolios != null ? portfolios.size() : 0, userId);
            
        if (portfolios == null || portfolios.isEmpty()) {
            log.warn("No portfolios found for user: {}", userId);
            return null;
        }
        
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

        PortfolioSummaryV1 finalSummary = buildPortfolioSummary(filteredPortfolios, userId, portfolioId, interval);
        
        log.info("Completed overviewPortfolio for user: {} and portfolio: {}", userId, portfolioId);
        return finalSummary;
    }

    /**
     * Builds portfolio summary from filtered portfolios with broker grouping
     * 
     * @param portfolios  the list of portfolios to process
     * @param userId      the user ID for logging
     * @param portfolioId the portfolio ID for logging (null if processing all portfolios)
     * @return the complete portfolio summary
     */
    private PortfolioSummaryV1 buildPortfolioSummary(List<PortfolioModelV1> portfolios, String userId, String portfolioId, TimeInterval interval) {
        String context = portfolioId != null ? "portfolio: " + portfolioId : "all portfolios";
        
        // Group by broker and create summary
        Map<BrokerType, BrokerPortfolioSummary> brokerSummaryMap = new HashMap<>();
        log.debug("Grouping portfolios by broker for user: {} and {}", userId, context);

        for (var portfolio : portfolios) {
            log.debug("Processing portfolio: ID={}, Broker={}, Value={}", 
                portfolio.getId(), portfolio.getBrokerType(), portfolio.getTotalValue());
                
            var portfolioSummary = portfolioMapper.toPortfolioModelV1(portfolio);
            brokerSummaryMap.computeIfAbsent(portfolio.getBrokerType(), brokerType -> portfolioSummary);
        }
        
        log.debug("Created broker summary map with {} entries for {}", brokerSummaryMap.size(), context);

        // Create final summary
        log.debug("Creating final portfolio summary for user: {} and {}", userId, context);
        PortfolioSummaryV1 finalSummary = getPortfolioSummary(portfolios);
        finalSummary.setBrokerPortfolios(brokerSummaryMap);
        
        log.info("Total portfolio value for user {} and {}: {}", 
            userId, context, finalSummary.getInvestmentValue());
            
        // Store in cache
        log.debug("Caching portfolio summary for user: {}", userId);
        portfolioSummaryRedisService.cachePortfolioSummary(finalSummary, userId, interval);
        
        log.info("Completed overviewPortfolio for user: {}", userId);
                return finalSummary;
    }

    private PortfolioSummaryV1 getPortfolioSummary(List<PortfolioModelV1> portfolios) {
        log.debug("Calculating total portfolio value from {} portfolios", portfolios.size());
        
        var totalValue = portfolios.stream().mapToDouble(PortfolioModelV1::getTotalValue).sum();
        log.debug("Calculated total value: {}", totalValue);
        
        var equityHoldings = portfolioHoldingsService.getHoldings(portfolios);
        var sectorialHoldings = enrichSectorialHoldings(equityHoldings);
        var maketCapHoldings = enrichMaketCapTypeHoldings(equityHoldings);

        // Calculate current value and overall gain/loss
        var currentValue = getCurrentValue(equityHoldings);
        var gainLoss = currentValue - totalValue;
        var gainLossPercentage = totalValue > 0 ? (gainLoss / totalValue) * 100 : 0.0;
        
        // Calculate today's gain/loss
        double todayGainLoss = calculateTodayGainLoss(equityHoldings);
        double todayGainLossPercentage = currentValue > 0 ? (todayGainLoss / currentValue) * 100 : 0.0;
        
        // Count gainers and losers
        int gainersCount = countGainers(equityHoldings, false);
        int losersCount = countLosers(equityHoldings, false);
        int todayGainersCount = countGainers(equityHoldings, true);
        int todayLosersCount = countLosers(equityHoldings, true);
        
        // Round all decimal values to two decimal places
        totalValue = roundToTwoDecimalPlaces(totalValue);
        currentValue = roundToTwoDecimalPlaces(currentValue);
        gainLoss = roundToTwoDecimalPlaces(gainLoss);
        gainLossPercentage = roundToTwoDecimalPlaces(gainLossPercentage);
        todayGainLoss = roundToTwoDecimalPlaces(todayGainLoss);
        todayGainLossPercentage = roundToTwoDecimalPlaces(todayGainLossPercentage);
        
        return PortfolioSummaryV1.builder()
            .investmentValue(totalValue)
            .currentValue(currentValue)
            .totalGainLoss(gainLoss)
            .totalGainLossPercentage(gainLossPercentage)
            .todayGainLoss(todayGainLoss)
            .todayGainLossPercentage(todayGainLossPercentage)
            .totalAssets(equityHoldings.size())
            .gainersCount(gainersCount)
            .losersCount(losersCount)
            .todayGainersCount(todayGainersCount)
            .todayLosersCount(todayLosersCount)
            .lastUpdated(LocalDateTime.now())
            .marketCapHoldings(maketCapHoldings)
            .sectorialHoldings(sectorialHoldings)
            .build();
    }

    private Double getCurrentValue(List<EquityHoldings> equityHoldings) {
        // Extract all stock symbols from equity holdings
        List<String> symbols = equityHoldings.stream()
            .filter(e -> e.getSymbol() != null)
            .map(EquityHoldings::getSymbol)
            .collect(Collectors.toList());
            
        if (symbols.isEmpty()) {
            log.warn("No valid symbols found in equity holdings");
            return 0.0;
        }
        
        log.info("Fetching market data for {} symbols", symbols.size());
        
        // Fetch current market data for all symbols in a single call
        Map<String, MarketData> marketDataMap = marketDataService.getMarketData(symbols);
        
        if (marketDataMap.isEmpty()) {
            log.warn("No market data available for any symbols");
            // Fall back to existing current values if available
            return equityHoldings.stream()
                .filter(e -> e.getCurrentValue() != null)
                .mapToDouble(EquityHoldings::getCurrentValue)
                .sum();
        }
        
        // Calculate current value for each holding using latest market data
        return equityHoldings.stream()
            .filter(e -> e.getSymbol() != null && e.getQuantity() != null)
            .mapToDouble(holding -> {
                MarketData data = marketDataMap.get(holding.getSymbol());
                if (data == null) {
                    log.debug("No market data for symbol: {}, using existing current value", holding.getSymbol());
                    return holding.getCurrentValue() != null ? holding.getCurrentValue() : 0.0;
                }
                
                Double currentPrice;
                // First check lastPrice (current price) and use it if available
                if (data.getLastPrice() != null) {
                    currentPrice = data.getLastPrice();
                } 
                // Fall back to OHLC close price if lastPrice is not available
                else if (data.getOhlc() != null) {
                    currentPrice = data.getOhlc().getClose();
                } else {
                    log.debug("No price data available for symbol: {}", holding.getSymbol());
                    return holding.getCurrentValue() != null ? holding.getCurrentValue() : 0.0;
                }
                
                // Update the holding with current price (side effect)
                holding.setCurrentPrice(currentPrice);
                Double currentValue = currentPrice * holding.getQuantity();
                holding.setCurrentValue(currentValue);
                
                // Calculate gain/loss if average buying price is available
                if (holding.getAverageBuyingPrice() != null) {
                    Double gainLoss = currentValue - (holding.getAverageBuyingPrice() * holding.getQuantity());
                    Double gainLossPercentage = ((currentPrice - holding.getAverageBuyingPrice()) / holding.getAverageBuyingPrice()) * 100;
                    holding.setGainLoss(gainLoss);
                    holding.setGainLossPercentage(gainLossPercentage);
                }
                
                return currentValue;
            })
            .sum();
    }

    private Map<String, List<EquityHoldings>> enrichSectorialHoldings(List<EquityHoldings> equityHoldings) {
        return equityHoldings.stream()
            .filter(e -> e.getSector() != null)
            .collect(Collectors.groupingBy(EquityHoldings::getSector));
    }

    private Map<String, List<EquityHoldings>> enrichMaketCapTypeHoldings(List<EquityHoldings> equityHoldings) {
        return equityHoldings.stream()
            .filter(e -> e.getMarketCap() != null)
            .collect(Collectors.groupingBy(EquityHoldings::getMarketCap));
    }

    /**
     * Calculate the total gain/loss for today across all holdings
     */
    private double calculateTodayGainLoss(List<EquityHoldings> equityHoldings) {
        return equityHoldings.stream()
            .filter(holding -> holding.getTodayGainLoss() != null)
            .mapToDouble(EquityHoldings::getTodayGainLoss)
            .sum();
    }
    
    /**
     * Count the number of holdings that have positive gain
     * @param today If true, count based on today's gain/loss, otherwise overall gain/loss
     */
    private int countGainers(List<EquityHoldings> equityHoldings, boolean today) {
        return (int) equityHoldings.stream()
            .filter(holding -> {
                if (today) {
                    return holding.getTodayGainLoss() != null && holding.getTodayGainLoss() > 0;
                } else {
                    return holding.getGainLoss() != null && holding.getGainLoss() > 0;
                }
            })
            .count();
    }
    
    /**
     * Count the number of holdings that have negative gain (loss)
     * @param today If true, count based on today's gain/loss, otherwise overall gain/loss
     */
    private int countLosers(List<EquityHoldings> equityHoldings, boolean today) {
        return (int) equityHoldings.stream()
            .filter(holding -> {
                if (today) {
                    return holding.getTodayGainLoss() != null && holding.getTodayGainLoss() < 0;
                } else {
                    return holding.getGainLoss() != null && holding.getGainLoss() < 0;
                }
            })
            .count();
    }
    
    /**
     * Utility method to round a double value to two decimal places
     */
    private double roundToTwoDecimalPlaces(double value) {
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
    
    private Optional<PortfolioSummaryV1> getCachedSummary(String userId, TimeInterval interval) {
        log.debug("Checking cache for portfolio summary - User: {}, Interval: {}", 
            userId, interval != null ? interval.getCode() : "null");
            
        Optional<PortfolioSummaryV1> cachedSummary = portfolioSummaryRedisService.getLatestSummary(userId, interval);
        if (cachedSummary.isPresent()) {
            log.info("Serving portfolio summary from cache - User: {}, Interval: {}", 
                userId, interval != null ? interval.getCode() : "null");
        } else {
            log.debug("No cached summary found for user: {}", userId);
        }
        
        return cachedSummary;
    }
}
