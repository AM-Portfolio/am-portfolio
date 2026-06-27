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
import com.portfolio.redis.service.EnrichedHoldingsCacheService;
import com.am.common.amcommondata.model.asset.equity.EquityModel;
import com.am.common.amcommondata.model.enums.AssetType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class StockHoldingUpdateConsumerService {

    private final ObjectMapper objectMapper;
    private final PortfolioHoldingsService portfolioHoldingsService;
    private final PortfolioCalculator portfolioCalculator;
    private final KafkaProducerService kafkaProducerService;
    private final EnrichedHoldingsCacheService enrichedHoldingsCacheService;

    @KafkaListener(topics = "${app.kafka.holding.topic}", groupId = "${app.kafka.holding.consumer.id}", containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message, Acknowledgment acknowledgment) {
        try {
            log.info("Received holding update message: {}", message);

            StockHoldingUpdateEvent event = objectMapper.readValue(message, StockHoldingUpdateEvent.class);
            log.info("Processing holding update for user: {}, symbol: {}", event.getUserId(), event.getSymbol());

            // Evict the enriched holdings cache so the next request recomputes
            enrichedHoldingsCacheService.evictEnrichedHoldingsCache(event.getUserId(), null);
            log.info("Evicted enriched holdings cache for user {} due to holding update", event.getUserId());

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

        // Map Summary Data (guard against null if calculateSummary returns null)
        if (summary != null) {
            event.setTotalValue(summary.getCurrentValue());
            event.setTotalInvestment(summary.getInvestmentValue());
            event.setTotalGainLoss(summary.getTotalGainLoss());
            event.setTotalGainLossPercentage(summary.getTotalGainLossPercentage());
            event.setTodayGainLoss(summary.getTodayGainLoss());
            event.setTodayGainLossPercentage(summary.getTodayGainLossPercentage());
        }

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
