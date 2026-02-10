package com.portfolio.kafka.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.portfolio.model.events.PortfolioUpdateEvent;
import com.portfolio.model.events.StockHoldingUpdateEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.portfolio.topic:am-portfolio-update}")
    private String topicName;

    @Value("${app.kafka.portfolio.stream.topic:am-portfolio-stream}")
    private String portfolioStreamTopicName;

    @Value("${app.kafka.holding.topic:am-holding-update}")
    private String holdingTopicName;

    public void sendPortfolioStreamMessage(PortfolioUpdateEvent portfolioUpdateEvent, String correlationId) {
        RecordHeaders headers = new RecordHeaders();
        headers.add("id", portfolioUpdateEvent.getId().toString().getBytes());
        headers.add("userId", portfolioUpdateEvent.getUserId().getBytes());
        headers.add("timestamp", String.valueOf(portfolioUpdateEvent.getTimestamp()).getBytes());

        // Add portfolioId to headers if present
        if (portfolioUpdateEvent.getPortfolioId() != null) {
            headers.add("portfolioId", portfolioUpdateEvent.getPortfolioId().getBytes());
        }

        // Add correlation ID to headers if present
        if (correlationId != null) {
            headers.add("X-Correlation-Id", correlationId.getBytes());
        }

        // Use correlationId as key if available, otherwise fall back to event ID
        String recordKey = correlationId != null ? correlationId : portfolioUpdateEvent.getId().toString();

        ProducerRecord<String, Object> record = new ProducerRecord<>(portfolioStreamTopicName, null,
                recordKey, portfolioUpdateEvent, headers);

        sendRecord(record);
    }

    public void sendMessage(PortfolioUpdateEvent portfolioUpdateEvent, String correlationId) {
        RecordHeaders headers = new RecordHeaders();
        headers.add("id", portfolioUpdateEvent.getId().toString().getBytes());
        headers.add("userId", portfolioUpdateEvent.getUserId().getBytes());
        headers.add("timestamp", String.valueOf(portfolioUpdateEvent.getTimestamp()).getBytes());

        if (correlationId != null) {
            headers.add("X-Correlation-Id", correlationId.getBytes());
        }

        ProducerRecord<String, Object> record = new ProducerRecord<>(topicName, null,
                portfolioUpdateEvent.getId().toString(), portfolioUpdateEvent, headers);

        sendRecord(record);
    }

    public void sendStockHoldingUpdate(StockHoldingUpdateEvent event) {
        RecordHeaders headers = new RecordHeaders();
        headers.add("id", event.getId() != null ? event.getId().getBytes() : "null".getBytes());
        headers.add("userId", event.getUserId().getBytes());
        headers.add("timestamp", String.valueOf(event.getTimestamp()).getBytes());

        ProducerRecord<String, Object> record = new ProducerRecord<>(holdingTopicName, null,
                event.getId(), event, headers);

        sendRecord(record);
    }

    private void sendRecord(ProducerRecord<String, Object> record) {
        kafkaTemplate.send(record)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Message sent successfully to topic: {}, partition: {}, offset: {}",
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    } else {
                        log.error("Failed to send message", ex);
                    }
                });
    }

}
