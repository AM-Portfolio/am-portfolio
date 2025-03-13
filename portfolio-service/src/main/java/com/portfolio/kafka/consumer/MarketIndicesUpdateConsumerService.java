package com.portfolio.kafka.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import com.portfolio.rediscache.service.MarketIndexIndicesRedisService;
import com.am.common.investment.model.events.MarketIndexIndicesPriceUpdateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

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
            log.info("Received market indices update message");
            log.debug("Message payload: {}", message);
            
            // Convert JSON string to MarketIndexIndicesPriceUpdateEvent
            MarketIndexIndicesPriceUpdateEvent event = objectMapper.readValue(message, MarketIndexIndicesPriceUpdateEvent.class);
            log.debug("Converted event: {}", objectMapper.writeValueAsString(event));
              
            // Process the event
            processMessage(event);
            
            // If processing was successful, acknowledge the message
            acknowledgment.acknowledge();
            log.info("Market indices update processed successfully");
        } catch (JsonProcessingException e) {
            log.error("Failed to process JSON message: {}", e.getMessage());
            log.debug("Failed message: {}", message, e);
        } catch (Exception e) {
            log.error("Failed to process message: {}", e.getMessage());
            log.debug("Failed message: {}", message, e);
        }
    }

    private void processMessage(MarketIndexIndicesPriceUpdateEvent event) throws JsonProcessingException {
        marketIndexIndicesRedisService.cacheMarketIndexIndicesUpdateBatch(event.getMarketIndices());
        log.debug("Cached market indices: {}", objectMapper.writeValueAsString(event.getMarketIndices()));
    }
}
