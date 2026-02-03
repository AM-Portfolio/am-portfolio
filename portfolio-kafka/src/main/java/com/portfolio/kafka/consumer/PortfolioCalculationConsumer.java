package com.portfolio.kafka.consumer;

import com.am.kafka.config.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
@RequiredArgsConstructor
public class PortfolioCalculationConsumer {

    // Inject your application service here to perform the actual calculation
    // private final PortfolioCalculationService calculationService;

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

            log.info("Received TRIGGER_CALCULATION event [TraceID: {}] for key: {}", correlationId, record.key());

            // TODO: Trigger the actual business logic
            // calculationService.calculatePortfolio(record.key());

            // For now, we just log completion
            log.info("Portfolio calculation completed [TraceID: {}]", correlationId);

        } catch (Exception e) {
            log.error("Error processing calculation trigger [TraceID: {}]", correlationId, e);
        } finally {
            MDC.clear();
        }
    }
}
