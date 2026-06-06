package com.portfolio.kafka.producer;

import com.portfolio.model.events.PortfolioUpdateEvent;
import com.portfolio.model.events.StockHoldingUpdateEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaProducerServiceTest {

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks private KafkaProducerService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "topicName", "am-portfolio-update");
        ReflectionTestUtils.setField(service, "portfolioStreamTopicName", "am-portfolio-stream");
        ReflectionTestUtils.setField(service, "holdingTopicName", "am-holding-update");
    }

    @SuppressWarnings("unchecked")
    private void stubSend() {
        SendResult<String, Object> result = mock(SendResult.class);
        RecordMetadata meta = new RecordMetadata(new TopicPartition("t", 0), 0, 0, 0, 0, 0);
        when(result.getRecordMetadata()).thenReturn(meta);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(result));
    }

    private PortfolioUpdateEvent buildEvent() {
        PortfolioUpdateEvent e = new PortfolioUpdateEvent();
        e.setId(UUID.randomUUID());
        e.setUserId("user1");
        e.setTimestamp(LocalDateTime.now());
        return e;
    }

    @Test void sendMessage_sendsToCorrectTopic() {
        stubSend();
        service.sendMessage(buildEvent(), "corr-1");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, Object>> cap = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(cap.capture());
        assertEquals("am-portfolio-update", cap.getValue().topic());
    }

    @Test void sendMessage_withoutCorrelationId() {
        stubSend();
        service.sendMessage(buildEvent(), null);
        verify(kafkaTemplate).send(any(ProducerRecord.class));
    }

    @Test void sendPortfolioStreamMessage_sendsToStreamTopic() {
        stubSend();
        var event = buildEvent();
        event.setPortfolioId("port-1");
        service.sendPortfolioStreamMessage(event, "corr-1");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, Object>> cap = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(cap.capture());
        assertEquals("am-portfolio-stream", cap.getValue().topic());
    }

    @Test void sendPortfolioStreamMessage_withoutPortfolioId() {
        stubSend();
        service.sendPortfolioStreamMessage(buildEvent(), "corr-1");
        verify(kafkaTemplate).send(any(ProducerRecord.class));
    }

    @Test void sendStockHoldingUpdate_sendsToHoldingTopic() {
        stubSend();
        StockHoldingUpdateEvent e = new StockHoldingUpdateEvent();
        e.setId("h1");
        e.setUserId("user1");
        e.setTimestamp(LocalDateTime.now());
        service.sendStockHoldingUpdate(e);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, Object>> cap = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(cap.capture());
        assertEquals("am-holding-update", cap.getValue().topic());
    }

    @Test void sendStockHoldingUpdate_nullId() {
        stubSend();
        StockHoldingUpdateEvent e = new StockHoldingUpdateEvent();
        e.setId(null);
        e.setUserId("user1");
        e.setTimestamp(LocalDateTime.now());
        service.sendStockHoldingUpdate(e);
        verify(kafkaTemplate).send(any(ProducerRecord.class));
    }
}
