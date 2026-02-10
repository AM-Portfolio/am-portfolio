package com.portfolio.kafka.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.kafka.config.KafkaTopics;
import com.portfolio.kafka.service.PortfolioCalculationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;

import jakarta.annotation.PostConstruct;

@Service
@Slf4j
@RequiredArgsConstructor
public class PortfolioCalculationConsumer {

    private final ObjectMapper objectMapper;
    private final PortfolioCalculationService portfolioCalculationService;

    @PostConstruct
    public void init() {
        log.info("PortfolioCalculationConsumer initialized and listening to topic: {}",
                KafkaTopics.TRIGGER_CALCULATION);
    }

    @KafkaListener(topics = KafkaTopics.TRIGGER_CALCULATION, groupId = "${spring.kafka.consumer.group-id}")
    public void listen(ConsumerRecord<String, String> record) {
        String correlationId = null;
        try {
            // Extract Tracing ID
            if (record.headers().lastHeader("X-Correlation-Id") != null) {
                correlationId = new String(record.headers().lastHeader("X-Correlation-Id").value(),
                        StandardCharsets.UTF_8);
                MDC.put("traceId", correlationId);
            }

            log.info("Key Received TRIGGER_CALCULATION event [TraceID: {}] for key: {}", correlationId, record.key());

            // Parse message to get UserId
            String message = record.value();
            JsonNode jsonNode = objectMapper.readTree(message);
            if (!jsonNode.has("userId")) {
                log.error("Received message without userId: {}", message);
                return;
            }
            String userId = jsonNode.get("userId").asText();

            // Delegate to Service
            portfolioCalculationService.processCalculation(userId, correlationId);

            log.info("Portfolio calculation completed successfully [TraceID: {}]", correlationId);

        } catch (Exception e) {
            log.error("Error processing calculation trigger [TraceID: {}]", correlationId, e);
        } finally {
            MDC.clear();
        }
    }
}
