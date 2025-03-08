package com.portfolio.kafka.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import com.am.common.investment.model.equity.EquityPrice;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.kafka.model.EquityPriceUpdateEvent;
import com.portfolio.rediscache.service.StockPriceRedisService;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.stock.consumer.enabled", havingValue = "true", matchIfMissing = false)
public class StockPriceUpdateEventConsumerService {

    private final ObjectMapper objectMapper;
    private final StockPriceRedisService stockPriceRedisService;

    @KafkaListener(topics = "${app.kafka.stock.topic}", 
                  groupId = "${spring.kafka.consumer.group-id}",
                  containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message, Acknowledgment acknowledgment) {
        try {
            log.info("Received stock price update message: {}", message);
            
            // Convert JSON string to EquityPriceUpdateEvent
            EquityPriceUpdateEvent event = objectMapper.readValue(message, EquityPriceUpdateEvent.class);
            log.info("Converted to stock price event: {}", event);
            
            // Process the event
            processStockPriceUpdate(event);
            
            // If processing was successful, acknowledge the message
            acknowledgment.acknowledge();
            log.info("Stock price update processed and acknowledged successfully");
        } catch (Exception e) {
            log.error("Failed to process stock price update message: {}. Error: {}", message, e.getMessage(), e);
        }
    }

    private void processStockPriceUpdate(EquityPriceUpdateEvent event) {
        List<EquityPrice> prices = event.getEquityPrices();
        if (prices != null && !prices.isEmpty()) {
            stockPriceRedisService.cacheEquityPriceUpdateBatch(prices)
                .exceptionally(ex -> {
                    log.error("Failed to cache stock prices: {}", ex.getMessage(), ex);
                    return null;
                });
        }
    }
}
