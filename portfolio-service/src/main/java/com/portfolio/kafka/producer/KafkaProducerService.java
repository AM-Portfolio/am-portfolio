package com.portfolio.kafka.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.portfolio.kafka.model.PortfolioUpdateEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.portfolio.topic}")
    private String topicName;


    public void sendMessage(PortfolioUpdateEvent portfolioUpdateEvent) {
        RecordHeaders headers = new RecordHeaders();
        headers.add("id", portfolioUpdateEvent.getId().toString().getBytes());
        headers.add("userId", portfolioUpdateEvent.getUserId().getBytes());
        headers.add("timestamp", String.valueOf(portfolioUpdateEvent.getTimestamp()).getBytes());

        ProducerRecord<String, Object> record = new ProducerRecord<>(topicName, null, 
            portfolioUpdateEvent.getId().toString(), portfolioUpdateEvent, headers);
        
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
