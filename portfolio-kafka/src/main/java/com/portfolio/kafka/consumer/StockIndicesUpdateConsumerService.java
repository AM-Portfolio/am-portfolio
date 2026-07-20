package com.portfolio.kafka.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import com.am.common.investment.model.events.StockInsidicesEventData;
import com.am.common.amcommondata.document.price.StockPriceDocument;
import com.am.common.amcommondata.service.price.StockPriceMongoService;
import com.portfolio.kafka.util.SymbolResolver;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class StockIndicesUpdateConsumerService {

    private final ObjectMapper objectMapper;
    private final StockPriceMongoService stockPriceMongoService;

    @KafkaListener(topics = "${app.kafka.stock.topic}", 
                  groupId = "${app.kafka.stock.consumer.id}",
                  containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message, Acknowledgment acknowledgment) {
        try {
            log.info("Received stock price update message: {}", message);
            
            // Convert JSON string to StockInsidicesEventData
            StockInsidicesEventData event = objectMapper.readValue(message, StockInsidicesEventData.class);
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

    private void processStockPriceUpdate(StockInsidicesEventData event) {
        if (event != null && event.getData() != null) {
            List<StockPriceDocument> docs = event.getData().stream()
                .filter(sd -> sd != null && sd.getSymbol() != null)
                .map(sd -> StockPriceDocument.builder()
                    .symbol(SymbolResolver.normalize(sd.getSymbol()))
                    .lastPrice(sd.getLastPrice())
                    .previousClose(sd.getPreviousClose())
                    .openPrice(sd.getOpen())
                    .highPrice(sd.getDayHigh())
                    .lowPrice(sd.getDayLow())
                    .timestamp(System.currentTimeMillis())
                    .updatedAt(LocalDateTime.now())
                    .build())
                .collect(Collectors.toList());
                
            if (!docs.isEmpty()) {
                stockPriceMongoService.saveAll(docs);
            }
        }
    }
}
