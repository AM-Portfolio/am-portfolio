package com.portfolio.kafka.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import com.portfolio.redis.service.MarketIndexIndicesRedisService;
import com.am.common.investment.model.events.MarketIndexIndicesPriceUpdateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.market-index.consumer.enabled", havingValue = "true", matchIfMissing = false)
public class MarketIndicesUpdateConsumerService {

    private final ObjectMapper objectMapper;
    private final MarketIndexIndicesRedisService marketIndexIndicesRedisService;

    @KafkaListener(topics = "${app.kafka.market-index.topic}", 
                  groupId = "${app.kafka.market-index.consumer.id}",
                  containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message, Acknowledgment acknowledgment) {
        try {
            log.info("Received message: {}", message);
            
            // Convert JSON string to PortfolioUpdateEvent
            MarketIndexIndicesPriceUpdateEvent event = objectMapper.readValue(message, MarketIndexIndicesPriceUpdateEvent.class);
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

    private void processMessage(MarketIndexIndicesPriceUpdateEvent event) {
        marketIndexIndicesRedisService.cacheMarketIndexIndicesUpdateBatch(event.getMarketIndices());
    }
}
