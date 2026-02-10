package com.portfolio.kafka.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.model.events.PortfolioUpdateEvent;
import com.portfolio.model.events.StockHoldingUpdateEvent;
import com.portfolio.kafka.producer.KafkaProducerService;
import com.portfolio.service.portfolio.PortfolioHoldingsService;
import com.portfolio.service.calculator.PortfolioCalculator;
import com.portfolio.model.portfolio.PortfolioHoldings;
import com.portfolio.model.portfolio.EquityHoldings;
import com.portfolio.model.portfolio.v1.PortfolioSummaryV1;
import com.portfolio.model.TimeInterval;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.enums.AssetType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.holding.consumer.enabled", havingValue = "true", matchIfMissing = false)
public class StockHoldingUpdateConsumerService {

    private final ObjectMapper objectMapper;
    private final PortfolioHoldingsService portfolioHoldingsService;
    private final PortfolioCalculator portfolioCalculator;
    private final KafkaProducerService kafkaProducerService;

    @KafkaListener(topics = "${app.kafka.holding.topic:am-holding-update}", groupId = "${app.kafka.holding.consumer.id:am-portfolio-holding-group}", containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message, Acknowledgment acknowledgment) {
        try {
            log.info("Received holding update message: {}", message);

            StockHoldingUpdateEvent event = objectMapper.readValue(message, StockHoldingUpdateEvent.class);
            log.info("Processing holding update for user: {}, symbol: {}", event.getUserId(), event.getSymbol());

            // 1. Force recalculation of portfolio holdings
            PortfolioHoldings updatedHoldings = portfolioHoldingsService.getPortfolioHoldings(
                    event.getUserId(),
                    TimeInterval.ONE_DAY);

            if (updatedHoldings != null) {
                // 2. Enrich Holdings with Market Data
                List<EquityHoldings> enrichedHoldings = portfolioCalculator
                        .enrichHoldings(updatedHoldings.getEquityHoldings());
                updatedHoldings.setEquityHoldings(enrichedHoldings);

                // 3. Perform Full Summary Calculation
                double totalInvestment = updatedHoldings.getEquityHoldings().stream()
                        .filter(h -> h.getInvestmentCost() != null)
                        .mapToDouble(EquityHoldings::getInvestmentCost)
                        .sum();

                PortfolioSummaryV1 summary = portfolioCalculator.calculateSummary(updatedHoldings.getEquityHoldings(),
                        totalInvestment);

                // 3. Publish update to Kafka for Real-time UI updates
                PortfolioUpdateEvent updateEvent = mapToUpdateEvent(updatedHoldings, summary, event.getUserId(),
                        event.getPortfolioId());

                log.info("Publishing REAL-TIME portfolio update event for user: {}", event.getUserId());
                kafkaProducerService.sendMessage(updateEvent, null);
            }

            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process holding update: {}", message, e);
        }
    }

    private PortfolioUpdateEvent mapToUpdateEvent(PortfolioHoldings holdings, PortfolioSummaryV1 summary, String userId,
            String portfolioId) {
        PortfolioUpdateEvent event = new PortfolioUpdateEvent();
        event.setId(UUID.randomUUID());
        event.setUserId(userId);
        event.setPortfolioId(portfolioId);
        event.setTimestamp(LocalDateTime.now());

        // Map Summary Data
        event.setTotalValue(summary.getCurrentValue());
        event.setTotalInvestment(summary.getInvestmentValue());
        event.setTotalGainLoss(summary.getTotalGainLoss());
        event.setTotalGainLossPercentage(summary.getTotalGainLossPercentage());
        event.setTodayGainLoss(summary.getTodayGainLoss());
        event.setTodayGainLossPercentage(summary.getTodayGainLossPercentage());

        // Map Holdings
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
                .currentPrice(holding.getCurrentPrice()) // Using new field in AssetModel
                .currentValue(holding.getCurrentValue())
                .investmentValue(holding.getInvestmentCost())
                .profitLoss(holding.getGainLoss()) // Mapping gainLoss to profitLoss
                .profitLossPercentage(holding.getGainLossPercentage())
                .todayProfitLoss(holding.getTodayGainLoss()) // Using new field in AssetModel
                .todayProfitLossPercentage(holding.getTodayGainLossPercentage()) // Using new field
                .build();
    }
}
