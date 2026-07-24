package com.portfolio.kafka.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import com.portfolio.model.events.StockPriceUpdateEvent;
import com.am.common.amcommondata.document.price.StockPriceDocument;
import com.am.common.amcommondata.service.price.StockPriceMongoService;
import com.portfolio.model.util.SymbolResolver;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Qualifier;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class StockPriceUpdateConsumerService {

    private final ObjectMapper objectMapper;
    private final StockPriceMongoService stockPriceMongoService;
    private final com.am.common.amcommondata.service.price.StockPriceHistoryMongoService stockPriceHistoryMongoService;
    
    @Qualifier("historyWriterExecutor")
    private final Executor historyWriterExecutor;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 15);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

    private boolean isMarketHours() {
        ZonedDateTime now = ZonedDateTime.now(IST);
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return false;
        LocalTime t = now.toLocalTime();
        return !t.isBefore(MARKET_OPEN) && !t.isAfter(MARKET_CLOSE);
    }

    @KafkaListener(topics = "${app.kafka.stock.topic}", 
                  groupId = "${app.kafka.stock.consumer.id}",
                  containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message, Acknowledgment acknowledgment) {
        try {
            log.info("Received stock price update message: {}", message);
            
            StockPriceUpdateEvent event = objectMapper.readValue(message, StockPriceUpdateEvent.class);
            log.info("Converted to stock price event: {}", event);
            
            processStockPriceUpdate(event);
            
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
            log.info("Stock price update processed and acknowledged successfully");
        } catch (Exception e) {
            log.error("Failed to process stock price update message: {}. Error: {}", message, e.getMessage(), e);
            if (acknowledgment != null) {
                acknowledgment.nack(java.time.Duration.ofSeconds(5));
                log.info("Message nacked and will be retried");
            }
        }
    }

    private void processStockPriceUpdate(StockPriceUpdateEvent event) {
        if (event != null && event.getData() != null && !event.getData().isEmpty()) {
            List<StockPriceDocument> docs = new java.util.ArrayList<>();
            for (StockPriceUpdateEvent.StockPriceData sd : event.getData()) {
                if (sd != null && sd.getSymbol() != null) {
                    StockPriceDocument doc = StockPriceDocument.builder()
                        .symbol(cleanSymbol(sd.getSymbol()))
                        .lastPrice(sd.getLastPrice())
                        .previousClose(sd.getPreviousClose())
                        .openPrice(sd.getOpen())
                        .highPrice(sd.getDayHigh())
                        .lowPrice(sd.getDayLow())
                        .timestamp(System.currentTimeMillis())
                        .updatedAt(LocalDateTime.now())
                        .build();
                    docs.add(doc);
                }
            }
            if (!docs.isEmpty()) {
                stockPriceMongoService.saveAll(docs);
                
                // HISTORY WRITE — fire-and-forget, market hours only, never blocks consumer
                if (isMarketHours()) {
                    final List<StockPriceDocument> snapshot = List.copyOf(docs);
                    CompletableFuture.runAsync(() -> {
                        try {
                            stockPriceHistoryMongoService.saveAll(snapshot);
                        } catch (Exception e) {
                            log.warn("[PriceHistory] Non-critical: history tick failed: {}", e.getMessage());
                        }
                    }, historyWriterExecutor);
                }
            }
        }
    }

    String cleanSymbol(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            return symbol;
        }

        int colonIndex = symbol.indexOf(':');
        String cleaned = symbol;
        if (colonIndex > 0 && colonIndex < symbol.length() - 1) {
            cleaned = symbol.substring(colonIndex + 1);
        }

        return SymbolResolver.normalize(cleaned);
    }
}
