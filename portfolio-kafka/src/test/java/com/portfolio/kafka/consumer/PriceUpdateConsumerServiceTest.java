package com.portfolio.kafka.consumer;

import com.am.common.investment.model.equity.EquityPrice;
import com.am.common.investment.model.events.EquityPriceUpdateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.redis.service.StockPriceRedisService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceUpdateConsumerServiceTest {

    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private StockPriceRedisService stockPriceRedisService;
    @Mock private Acknowledgment acknowledgment;
    @InjectMocks private PriceUpdateConsumerService consumer;

    @Test void consume_validEvent_cachesAndAcknowledges() throws Exception {
        EquityPriceUpdateEvent event = new EquityPriceUpdateEvent();
        EquityPrice price = new EquityPrice();
        price.setSymbol("AAPL");
        event.setEquityPrices(List.of(price));

        String json = objectMapper.writeValueAsString(event);
        when(stockPriceRedisService.cacheEquityPriceUpdateBatch(any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        consumer.consume(json, acknowledgment);

        verify(stockPriceRedisService).cacheEquityPriceUpdateBatch(any());
        verify(acknowledgment).acknowledge();
    }

    @Test void consume_emptyPrices_acknowledgesWithoutCache() throws Exception {
        EquityPriceUpdateEvent event = new EquityPriceUpdateEvent();
        event.setEquityPrices(null);

        String json = objectMapper.writeValueAsString(event);
        consumer.consume(json, acknowledgment);

        verifyNoInteractions(stockPriceRedisService);
        verify(acknowledgment).acknowledge();
    }

    @Test void consume_invalidJson_doesNotThrow() {
        consumer.consume("bad-json", acknowledgment);
        verifyNoInteractions(stockPriceRedisService);
        verify(acknowledgment, never()).acknowledge();
    }
}
