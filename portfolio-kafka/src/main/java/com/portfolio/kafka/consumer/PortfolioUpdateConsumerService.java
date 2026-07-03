package com.portfolio.kafka.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import com.portfolio.model.events.PortfolioUpdateEvent;
import com.portfolio.model.mapper.PortfolioMapperv1;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.kafka.publisher.PortfolioEventPublisher;
import org.springframework.context.annotation.Lazy;
import com.portfolio.redis.service.PortfolioHoldingsRedisService;

@Slf4j
@Service
@Lazy(false)
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class PortfolioUpdateConsumerService {

    private final ObjectMapper objectMapper;
    private final PortfolioMapperv1 portfolioMapper;
    private final PortfolioService portfolioService;
    private final PortfolioEventPublisher portfolioEventPublisher;
    private final PortfolioHoldingsRedisService portfolioHoldingsRedisService;


    @KafkaListener(topics = "${app.kafka.portfolio.topic}", groupId = "${app.kafka.portfolio.consumer.id}", containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message, Acknowledgment acknowledgment) {
        try {
            log.info("Received message: {}", message);

            com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(message);
            
            // EDGE CASE FIX: We check for 'portfolioId' instead of 'source' 
            // because 'portfolioId' is unique to the Document parser's event.
            // If Trade Management ever adds a 'source' field in the future, this won't break.
            if (rootNode.has("portfolioId")) {
                // Parse as PortfolioUpdateEvent (e.g. from Document Parser)
                PortfolioUpdateEvent event = objectMapper.treeToValue(rootNode, PortfolioUpdateEvent.class);
                log.info("Converted to PortfolioUpdateEvent: {}", event);
                processDocumentMessage(event);
            } else {
                // Parse as TradePortfolioSyncEvent (from Trade Management)
                com.portfolio.model.events.trade.TradePortfolioSyncEvent event = objectMapper.treeToValue(rootNode, com.portfolio.model.events.trade.TradePortfolioSyncEvent.class);
                log.info("Converted to TradePortfolioSyncEvent: {}", event);
                processTradeMessage(event);
            }

            // If processing was successful, acknowledge the message
            acknowledgment.acknowledge();
            log.info("Message processed and acknowledged successfully");
        } catch (Exception e) {
            log.error("Failed to process message: {}. Error: {}", message, e.getMessage(), e);
        }
    }

    private void processDocumentMessage(PortfolioUpdateEvent event) {
        PortfolioModelV1 portfolioModel = portfolioMapper.toPortfolioModelV1(event);
        PortfolioModelV1 saved = portfolioService.upsertDocumentPortfolio(portfolioModel);
        if (saved != null && saved.getOwner() != null) {
            portfolioHoldingsRedisService.evictPortfolioHoldings(saved.getOwner(), saved.getId() != null ? saved.getId().toString() : null);
        }
        publishUpdate(saved, event.getSource(), event.getPortfolioId());
    }

    private void processTradeMessage(com.portfolio.model.events.trade.TradePortfolioSyncEvent event) {
        PortfolioModelV1 portfolioModel = portfolioMapper.toPortfolioModelV1(event);
        PortfolioModelV1 saved = portfolioService.updateTradePortfolio(portfolioModel);
        if (saved != null && saved.getOwner() != null) {
            portfolioHoldingsRedisService.evictPortfolioHoldings(saved.getOwner(), saved.getId() != null ? saved.getId().toString() : null);
        }
        publishUpdate(saved, "TRADE", event.getId());
    }

    private void publishUpdate(PortfolioModelV1 saved, String source, String originalId) {
        if (saved != null) {
            portfolioEventPublisher.publishPortfolioUpdate(saved);
        } else {
            log.warn("[Consumer] Portfolio save returned null for source='{}', portfolioId='{}'. Event will NOT be published downstream.",
                     source, originalId);
        }
    }
}
