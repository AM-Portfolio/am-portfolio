package com.portfolio.kafka.service;

import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.enums.AssetType;
import com.portfolio.kafka.producer.KafkaProducerService;
import com.portfolio.model.TimeInterval;
import com.portfolio.model.events.PortfolioUpdateEvent;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.model.portfolio.PortfolioHoldings;
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;
import com.portfolio.service.calculator.PortfolioCalculator;
import com.portfolio.service.portfolio.PortfolioHoldingsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class PortfolioCalculationService {

    private final PortfolioHoldingsService portfolioHoldingsService;
    private final PortfolioCalculator portfolioCalculator;
    private final KafkaProducerService kafkaProducerService;
    private final PortfolioService portfolioService;

    public void processCalculation(String userId, String portfolioId, String correlationId) {
        log.info("Processing portfolio calculation in service for UserID: {}, PortfolioID: {}", userId, portfolioId);

        if (portfolioId == null || portfolioId.isEmpty()) {
            processAllPortfolios(userId, correlationId);
            return;
        }

        calculateAndPublish(userId, portfolioId, correlationId);
    }

    private void processAllPortfolios(String userId, String correlationId) {
        List<PortfolioModelV1> portfolios = portfolioService.getPortfoliosByUserId(userId);
        if (portfolios == null || portfolios.isEmpty()) {
            log.warn("No portfolios found for user: {}", userId);
            return;
        }

        log.info("Bootstrap-all: publishing calculation for {} portfolio(s), userId={}", portfolios.size(), userId);
        for (PortfolioModelV1 portfolio : portfolios) {
            if (portfolio.getId() == null) {
                continue;
            }
            calculateAndPublish(userId, portfolio.getId().toString(), correlationId);
        }
    }

    private void calculateAndPublish(String userId, String portfolioId, String correlationId) {
        try {
            PortfolioHoldings holdings = portfolioHoldingsService.getPortfolioHoldings(
                    userId, portfolioId, TimeInterval.ONE_DAY);

            if (holdings == null || holdings.getEquityHoldings() == null || holdings.getEquityHoldings().isEmpty()) {
                log.warn("No holdings found for user: {}, portfolioId: {}", userId, portfolioId);
                return;
            }

            double totalInvestment = holdings.getEquityHoldings().stream()
                    .filter(h -> h.getInvestmentCost() != null)
                    .mapToDouble(EquityHoldings::getInvestmentCost)
                    .sum();

            List<EquityHoldings> enrichedHoldings = portfolioCalculator.enrichHoldings(holdings.getEquityHoldings());
            PortfolioSummaryV1 summary = portfolioCalculator.calculateSummary(enrichedHoldings, totalInvestment);

            PortfolioUpdateEvent updateEvent = mapToUpdateEvent(holdings, summary, userId, portfolioId);

            log.info("Publishing calculated portfolio update for UserID: {}, PortfolioID: {}", userId, portfolioId);
            kafkaProducerService.sendMessage(updateEvent, correlationId);
            kafkaProducerService.sendPortfolioStreamMessage(updateEvent, correlationId);
        } catch (Exception e) {
            log.error("Error executing calculation logic for user: {}, portfolioId: {} [TraceID: {}]",
                    userId, portfolioId, correlationId, e);
            throw e;
        }
    }

    private PortfolioUpdateEvent mapToUpdateEvent(PortfolioHoldings holdings, PortfolioSummaryV1 summary, String userId,
            String portfolioId) {
        PortfolioUpdateEvent event = new PortfolioUpdateEvent();
        event.setId(UUID.randomUUID());
        event.setUserId(userId);
        event.setPortfolioId(portfolioId);
        event.setTimestamp(LocalDateTime.now());

        if (summary != null) {
            event.setTotalValue(summary.getCurrentValue());
            event.setTotalInvestment(summary.getInvestmentValue());
            event.setTotalGainLoss(summary.getTotalGainLoss());
            event.setTotalGainLossPercentage(summary.getTotalGainLossPercentage());
            event.setTodayGainLoss(summary.getTodayGainLoss());
            event.setTodayGainLossPercentage(summary.getTodayGainLossPercentage());
        }

        if (holdings.getEquityHoldings() != null) {
            List<EquityModel> equityModels = holdings.getEquityHoldings().stream()
                    .map(this::mapToEquityModel)
                    .collect(Collectors.toList());
            event.setEquities(equityModels);
        }

        return event;
    }

    private EquityModel mapToEquityModel(EquityHoldings holding) {
        return EquityModel.builder()
                .assetType(AssetType.EQUITY)
                .isin(holding.getIsin())
                .symbol(holding.getSymbol())
                .name(holding.getName())
                .quantity(holding.getQuantity())
                .avgBuyingPrice(holding.getAverageBuyingPrice())
                .currentPrice(holding.getCurrentPrice())
                .currentValue(holding.getCurrentValue())
                .investmentValue(holding.getInvestmentCost())
                .profitLoss(holding.getGainLoss())
                .profitLossPercentage(holding.getGainLossPercentage())
                .todayProfitLoss(holding.getTodayGainLoss())
                .todayProfitLossPercentage(holding.getTodayGainLossPercentage())
                .sector(holding.getSector())
                .industry(holding.getIndustry())
                .marketCap(holding.getMarketCapCategory())
                .build();
    }
}
