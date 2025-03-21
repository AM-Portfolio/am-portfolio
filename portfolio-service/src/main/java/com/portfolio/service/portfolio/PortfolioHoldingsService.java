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
import com.portfolio.rediscache.service.PortfolioHoldingsRedisService;
import com.portfolio.rediscache.service.StockPriceRedisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioHoldingsService {
    
    private final PortfolioService portfolioService;
    private final PortfolioHoldingsMapper portfolioHoldingsMapper;
    private final StockPriceRedisService stockPriceRedisService;
    private final PortfolioHoldingsRedisService portfolioHoldingsRedisService;

    public PortfolioHoldings getPortfolioHoldings(String userId, TimeInterval interval) {
        log.info("Starting getPortfolioHoldings - User: {}, Interval: {}", userId, interval != null ? interval.getCode() : "null");
        
        Optional<PortfolioHoldings> cachedHoldings = getCachedHoldings(userId, interval);
        if (cachedHoldings.isPresent()) {
            log.info("Returning cached portfolio holdings for user: {}", userId);
            return cachedHoldings.get();
        }
        
        log.info("Cache miss for portfolio holdings - User: {}, fetching from source", userId);
        var portfolios = portfolioService.getPortfoliosByUserId(userId);
        if (portfolios == null) {
            log.warn("No portfolios found for user: {}", userId);
            return null;
        }
        log.info("Found {} portfolios for user: {}", portfolios.size(), userId);
        var portfolioHoldings = getPortfolioHoldings(portfolios);

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
        
        return equityHoldings.stream()
            .map(this::enrichStockPriceAndPerformance)
            .toList();
    }

    private EquityHoldings enrichStockPriceAndPerformance(EquityHoldings equityHoldings) {
        log.debug("Enriching equity holding: Symbol={}, Quantity={}", 
            equityHoldings.getSymbol(), equityHoldings.getQuantity());
            
        var latestPrice = stockPriceRedisService.getLatestPrice(equityHoldings.getSymbol());
        if (latestPrice.isPresent()) {
            var currentPrice = latestPrice.get().getClosePrice();
            var currentValue = currentPrice * equityHoldings.getQuantity();
            
            log.debug("Price data found for {}: Price={}, Value={}", 
                equityHoldings.getSymbol(), currentPrice, currentValue);
                
            equityHoldings.setCurrentPrice(currentPrice);
            equityHoldings.setCurrentValue(currentValue);
            equityHoldings.setGainLoss(currentValue - equityHoldings.getInvestmentCost());
            equityHoldings.setGainLossPercentage(equityHoldings.getGainLoss() / equityHoldings.getInvestmentCost());
            
            log.debug("Calculated performance for {}: GainLoss={}, GainLossPercentage={}%", 
                equityHoldings.getSymbol(), equityHoldings.getGainLoss(), 
                equityHoldings.getGainLossPercentage() * 100);
        } else {
            log.warn("No price data found for symbol: {}", equityHoldings.getSymbol());
        }
        return equityHoldings;
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
