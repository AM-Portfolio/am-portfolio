package com.portfolio.kafka.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.kafka.config.KafkaTopics;
import com.portfolio.kafka.service.PortfolioCalculationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
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
        String traceId = null;
        try {
            String message = record.value();
            JsonNode jsonNode = objectMapper.readTree(message);

            // ── Step 1: Extract traceId/spanId from the JSON payload body (primary source).
            // The producer embeds trace context inside the message body, not in Kafka headers.
            if (jsonNode.has("traceId") && !jsonNode.get("traceId").asText("").isBlank()) {
                traceId = jsonNode.get("traceId").asText();
            }
            if (jsonNode.has("spanId") && !jsonNode.get("spanId").asText("").isBlank()) {
                MDC.put("spanId", jsonNode.get("spanId").asText());
            }

            // ── Step 2: Fallback — check Kafka header "X-Correlation-Id"
            if (traceId == null && record.headers().lastHeader("X-Correlation-Id") != null) {
                traceId = new String(record.headers().lastHeader("X-Correlation-Id").value(),
                        StandardCharsets.UTF_8);
            }

            // ── Step 3: Last resort — generate a fresh UUID so logs are always traceable
            if (traceId == null) {
                traceId = UUID.randomUUID().toString().replace("-", "");
            }

            // ── Step 4: Populate MDC — all downstream log statements on this thread pick this up
            MDC.put("traceId", traceId);

            log.info("Received TRIGGER_CALCULATION event for key: {} | traceId: {}", record.key(), traceId);

            if (!jsonNode.has("userId")) {
                log.error("Received message without userId: {}", message);
                return;
            }
            String userId = jsonNode.get("userId").asText();

            // Extract portfolioId (optional — backward compatibility)
            String portfolioId = jsonNode.has("portfolioId") ? jsonNode.get("portfolioId").asText() : null;

            // Delegate to Service
            portfolioCalculationService.processCalculation(userId, portfolioId, traceId);

            log.info("Portfolio calculation completed successfully | traceId: {}", traceId);

        } catch (Exception e) {
            log.error("Error processing calculation trigger | traceId: {}", traceId, e);
        } finally {
            // Targeted remove — NOT MDC.clear() — to avoid wiping Micrometer's span context
            MDC.remove("traceId");
            MDC.remove("spanId");
        }
    }
}
