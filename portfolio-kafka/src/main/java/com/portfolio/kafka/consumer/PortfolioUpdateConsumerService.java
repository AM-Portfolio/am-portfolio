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


    @KafkaListener(topics = "${app.kafka.portfolio.topic}", groupId = "${app.kafka.portfolio.consumer.id}", containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message, Acknowledgment acknowledgment) {
        try {
            log.info("Received message: {}", message);

            // Convert JSON string to PortfolioUpdateEvent
            PortfolioUpdateEvent event = objectMapper.readValue(message, PortfolioUpdateEvent.class);
            log.info("Converted to event: {}", event);

            // Process the event
            processMessage(event);

            // If processing was successful, acknowledge the message
            acknowledgment.acknowledge();
            log.info("Message processed and acknowledged successfully");
        } catch (Exception e) {
            log.error("Failed to process message: {}. Error: {}", message, e.getMessage(), e);
        }
    }

    private void processMessage(PortfolioUpdateEvent event) {
        log.info("[Consumer] tradeAction='{}' for portfolioId='{}'", event.getTradeAction(), event.getPortfolioId());
        
        PortfolioModelV1 portfolioModel = portfolioMapper.toPortfolioModelV1(event);
        PortfolioModelV1 saved;
        
        if ("TRADE".equalsIgnoreCase(event.getSource())) {
            saved = portfolioService.updateTradePortfolio(portfolioModel);
        } else {
            saved = portfolioService.upsertDocumentPortfolio(portfolioModel);
        }

        if (saved != null) {
            portfolioEventPublisher.publishPortfolioUpdate(saved);
        } else {
            log.warn("[Consumer] Portfolio save returned null for source='{}', portfolioId='{}'. Event will NOT be published downstream.",
                     event.getSource(), event.getPortfolioId());
        }
    }
}
