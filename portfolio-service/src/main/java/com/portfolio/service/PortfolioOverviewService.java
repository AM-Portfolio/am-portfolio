package com.portfolio.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.model.enums.BrokerType;
import com.am.common.amcommondata.service.PortfolioService;
import com.portfolio.mapper.PortfolioMapperv1;
import com.portfolio.mapper.holdings.PortfolioHoldingsMapper;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.model.portfolio.PortfolioHoldings;
import com.portfolio.model.portfolio.v1.BrokerPortfolioSummary;
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;
import com.portfolio.rediscache.service.StockPriceRedisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioOverviewService {
    
    private final PortfolioService portfolioService;
    private final PortfolioMapperv1 portfolioMapper;
    private final PortfolioHoldingsMapper portfolioHoldingsMapper;
    private final StockPriceRedisService stockPriceRedisService;

    public PortfolioSummaryV1 overviewPortfolio(String userId) {
        var portfolios = portfolioService.getPortfoliosByUserId(userId);
        if (portfolios == null) {
            return null;
        }

        // Group by broker and create summary
        Map<BrokerType, BrokerPortfolioSummary> brokerSummaryMap = new HashMap<>();

        for (var portfolio : portfolios) {
            var portfolioSummary = portfolioMapper.toPortfolioModelV1(portfolio);

            brokerSummaryMap.computeIfAbsent(portfolio.getBrokerType(), brokerType -> portfolioSummary);
        }

        // Create final summary
        PortfolioSummaryV1 finalSummary = getPortfolioSummary(portfolios);
        finalSummary.setBrokerPortfolios(brokerSummaryMap);

        return finalSummary;
    }

    private PortfolioSummaryV1 getPortfolioSummary(List<PortfolioModelV1> portfolio) {
        var totalValue = portfolio.stream().mapToDouble(PortfolioModelV1::getTotalValue).sum();
        return PortfolioSummaryV1.builder()
            .totalValue(totalValue)
            .lastUpdated(LocalDateTime.now())
            .build();
    }

    public PortfolioHoldings getPortfolioHoldings(String userId) {
        var portfolios = portfolioService.getPortfoliosByUserId(userId);
        if (portfolios == null) {
            return null;
        }
        var portfolioHoldings = portfolioHoldingsMapper.toPortfolioHoldingsV1(portfolios);
        portfolioHoldings.setEquityHoldings(enrichStockPriceAndPerformance(portfolioHoldings.getEquityHoldings()));
        return portfolioHoldings;
    }

    private List<EquityHoldings> enrichStockPriceAndPerformance(List<EquityHoldings> equityHoldings) {
        return equityHoldings.stream()
            .map(this::enrichStockPriceAndPerformance)
            .toList();
    }

    private EquityHoldings enrichStockPriceAndPerformance(EquityHoldings equityHoldings) {
        var latestPrice = stockPriceRedisService.getLatestPrice(equityHoldings.getSymbol());
        if (latestPrice.isPresent()) {
            var currentPrice = latestPrice.get().getClosePrice();
            var currentValue = currentPrice * equityHoldings.getQuantity();
            equityHoldings.setCurrentPrice(currentPrice);
            equityHoldings.setCurrentValue(currentValue);
            equityHoldings.setGainLoss(currentValue - equityHoldings.getInvestmentCost());
            equityHoldings.setGainLossPercentage(equityHoldings.getGainLoss() / equityHoldings.getInvestmentCost());
        }
        return equityHoldings;
    }
}
