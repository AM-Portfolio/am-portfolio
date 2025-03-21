package com.portfolio.service.portfolio;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.enums.BrokerType;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.mapper.PortfolioMapperv1;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.model.portfolio.v1.BrokerPortfolioSummary;
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;
import com.portfolio.rediscache.service.PortfolioSummaryRedisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioOverviewService {
    
    private final PortfolioService portfolioService;
    private final PortfolioHoldingsService portfolioHoldingsService;
    private final PortfolioMapperv1 portfolioMapper;
    private final PortfolioSummaryRedisService portfolioSummaryRedisService;

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
            
        if (portfolios == null) {
            log.warn("No portfolios found for user: {}", userId);
            return null;
        }

        // Group by broker and create summary
        Map<BrokerType, BrokerPortfolioSummary> brokerSummaryMap = new HashMap<>();
        log.debug("Grouping portfolios by broker for user: {}", userId);

        for (var portfolio : portfolios) {
            log.debug("Processing portfolio: ID={}, Broker={}, Value={}", 
                portfolio.getId(), portfolio.getBrokerType(), portfolio.getTotalValue());
                
            var portfolioSummary = portfolioMapper.toPortfolioModelV1(portfolio);
            brokerSummaryMap.computeIfAbsent(portfolio.getBrokerType(), brokerType -> portfolioSummary);
        }
        
        log.debug("Created broker summary map with {} entries", brokerSummaryMap.size());

        // Create final summary
        log.debug("Creating final portfolio summary for user: {}", userId);
        PortfolioSummaryV1 finalSummary = getPortfolioSummary(portfolios);
        finalSummary.setBrokerPortfolios(brokerSummaryMap);
        
        log.info("Total portfolio value for user {}: {}", userId, finalSummary.getInvestmentValue());

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


        var currentValue = getCurrentValue(equityHoldings);
        var gainLoss = totalValue - currentValue;
        var gainLossPercentage = (gainLoss / totalValue) * 100;
        
        return PortfolioSummaryV1.builder()
            .investmentValue(totalValue)
            .currentValue(currentValue)
            .lastUpdated(LocalDateTime.now())
            .marketCapHoldings(maketCapHoldings)
            .sectorialHoldings(sectorialHoldings)
            .totalGainLoss(gainLoss)
            .totalGainLossPercentage(gainLossPercentage)
            .build();
    }

    private Double getCurrentValue(List<EquityHoldings> equityHoldings) {
        return equityHoldings.stream()
            .filter(e -> e.getIsin() != null && e.getCurrentValue() != null)
            .mapToDouble(EquityHoldings::getCurrentValue)
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
