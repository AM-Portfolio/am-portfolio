package com.portfolio.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.kafka.service.PortfolioCalculationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioCalculationConsumerTest {

    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private PortfolioCalculationService calculationService;
    @InjectMocks private PortfolioCalculationConsumer consumer;

    @Test void listen_validMessage_delegatesToService() {
        String json = "{\"userId\":\"u1\",\"portfolioId\":\"p1\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("am-trigger-calculation", 0, 0, null, json);
        consumer.listen(record);
        verify(calculationService).processCalculation("u1", "p1", null);
    }

    @Test void listen_withCorrelationId_propagatesTraceId() {
        String json = "{\"userId\":\"u1\",\"portfolioId\":\"p1\"}";
        RecordHeaders headers = new RecordHeaders();
        headers.add("X-Correlation-Id", "trace-123".getBytes());
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "am-trigger-calculation", 0, 0, ConsumerRecord.NO_TIMESTAMP, null,
                0, 0, null, json, headers, java.util.Optional.empty());
        consumer.listen(record);
        verify(calculationService).processCalculation("u1", "p1", "trace-123");
    }

    @Test void listen_missingUserId_doesNotDelegate() {
        String json = "{\"portfolioId\":\"p1\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("am-trigger-calculation", 0, 0, null, json);
        consumer.listen(record);
        verifyNoInteractions(calculationService);
    }

    @Test void listen_noPortfolioId_passesNull() {
        String json = "{\"userId\":\"u1\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("am-trigger-calculation", 0, 0, null, json);
        consumer.listen(record);
        verify(calculationService).processCalculation("u1", null, null);
    }

    @Test void listen_invalidJson_doesNotThrow() {
        String json = "not-json";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("am-trigger-calculation", 0, 0, null, json);
        consumer.listen(record); // should not throw
        verifyNoInteractions(calculationService);
    }

    @Test void listen_serviceThrows_doesNotPropagate() {
        String json = "{\"userId\":\"u1\"}";
        doThrow(new RuntimeException("boom")).when(calculationService).processCalculation(any(), any(), any());
        ConsumerRecord<String, String> record = new ConsumerRecord<>("am-trigger-calculation", 0, 0, null, json);
        consumer.listen(record); // should not throw
    }
}
