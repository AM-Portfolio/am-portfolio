package com.portfolio.kafka.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import com.portfolio.model.events.PortfolioUpdateEvent;
import com.portfolio.model.mapper.PortfolioMapperv1;
import com.am.common.amcommondata.model.PortfolioModelV1;
import com.am.common.amcommondata.service.PortfolioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.kafka.publisher.PortfolioEventPublisher;
import org.springframework.context.annotation.Lazy;
import com.portfolio.redis.service.PortfolioHoldingsRedisService;

import java.time.Duration;

/**
 * Consumes messages from the am-portfolio Kafka topic.
 *
 * <p>Messages can originate from two sources:
 * <ul>
 *   <li><b>Document Parser</b>  — serialised as {@link PortfolioUpdateEvent} (contains a {@code portfolioId} field)</li>
 *   <li><b>Trade Management</b> — serialised as {@link com.portfolio.model.events.trade.TradePortfolioSyncEvent}
 *       (no {@code portfolioId} field; the portfolio is identified via the top-level {@code id} field)</li>
 * </ul>
 *
 * <h3>Idempotency / de-duplication</h3>
 * <p>Kafka provides <em>at-least-once</em> delivery guarantees. This means the same message can be
 * delivered more than once (e.g. after a consumer restart before the offset was committed, or after a
 * network blip). Without protection this would cause duplicate equity holdings in MongoDB.
 *
 * <p>To guard against this, every consumed message is fingerprinted with a Redis key that encodes
 * the source, the portfolio-id and the Kafka offset.  The key is set <b>after</b> successful
 * processing with a TTL of {@value #DEDUP_TTL_HOURS} hours.  A second delivery of the same message
 * hits the Redis key, is recognised as a duplicate and is acknowledged-and-skipped without touching
 * MongoDB.
 */
@Slf4j
@Service
@Lazy(false)
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class PortfolioUpdateConsumerService {

    // ── De-duplication constants ─────────────────────────────────────────────
    private static final String DEDUP_KEY_PREFIX = "kafka:dedup:portfolio-update:";
    private static final int    DEDUP_TTL_HOURS  = 24;

    // ── Dependencies ─────────────────────────────────────────────────────────
    private final ObjectMapper                  objectMapper;
    private final PortfolioMapperv1             portfolioMapper;
    private final PortfolioService              portfolioService;
    private final PortfolioEventPublisher       portfolioEventPublisher;
    private final PortfolioHoldingsRedisService portfolioHoldingsRedisService;
    private final com.portfolio.redis.service.PortfolioSummaryRedisService portfolioSummaryRedisService;
    private final StringRedisTemplate           stringRedisTemplate;

    @Value("${app.kafka.portfolio.consumer.id:am-portfolio-consumer-group}")
    private String consumerGroupId;

    // ── Kafka listener ───────────────────────────────────────────────────────

    @jakarta.annotation.PostConstruct
    public void init() {
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @KafkaListener(
            topics          = "${app.kafka.portfolio.topic}",
            groupId         = "${app.kafka.portfolio.consumer.id}",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(
            org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {

        String message = record.value();
        long   offset  = record.offset();
        int    partition = record.partition();
        String topic   = record.topic();

        log.info("Received message from topic={} partition={} offset={}", topic, partition, offset);

        try {
            com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(message);

            // ── Determine message type ────────────────────────────────────────
            // PortfolioUpdateEvent (Document Parser) carries 'portfolioId' or 'equities'.
            // TradePortfolioSyncEvent (Trade Management) carries portfolio id in top-level 'id'.
            if (rootNode.has("portfolioId") || rootNode.has("equities")) {
                // ── Document-parser path ──────────────────────────────────────
                PortfolioUpdateEvent event = objectMapper.treeToValue(rootNode, PortfolioUpdateEvent.class);
                String pid = event.getPortfolioId() != null ? event.getPortfolioId()
                        : (event.getId() != null ? event.getId().toString() : "doc-" + offset);
                log.info("Parsed PortfolioUpdateEvent for portfolioId={} userId={}", pid, event.getUserId());

                String dedupKey = buildDedupKey("DOC", pid, offset, partition);
                if (isDuplicate(dedupKey)) {
                    log.warn("[DEDUP] Skipping already-processed Document message: portfolioId={} offset={}", pid, offset);
                    acknowledgment.acknowledge();
                    return;
                }

                processDocumentMessage(event);
                markProcessed(dedupKey);

            } else {
                // ── Trade-management path ─────────────────────────────────────
                com.portfolio.model.events.trade.TradePortfolioSyncEvent event =
                        objectMapper.treeToValue(rootNode, com.portfolio.model.events.trade.TradePortfolioSyncEvent.class);
                log.info("Parsed TradePortfolioSyncEvent for portfolioId={}", event.getId());

                String dedupKey = buildDedupKey("TRADE", event.getId(), offset, partition);
                if (isDuplicate(dedupKey)) {
                    log.warn("[DEDUP] Skipping already-processed Trade message: portfolioId={} offset={}", event.getId(), offset);
                    acknowledgment.acknowledge();
                    return;
                }

                processTradeMessage(event);
                markProcessed(dedupKey);
            }

            // Acknowledge ONLY after successful processing
            acknowledgment.acknowledge();
            log.info("Message acknowledged — topic={} partition={} offset={}", topic, partition, offset);

        } catch (Exception e) {
            // Do NOT acknowledge — let Kafka redeliver for retry.
            // The de-duplication key is NOT written on failure, so the retry
            // will be processed normally.
            log.error("Failed to process message at topic={} partition={} offset={}: {}",
                    topic, partition, offset, e.getMessage(), e);
        }
    }

    // ── Message processing ───────────────────────────────────────────────────

    private void processDocumentMessage(PortfolioUpdateEvent event) {
        PortfolioModelV1 portfolioModel = portfolioMapper.toPortfolioModelV1(event);
        PortfolioModelV1 saved = portfolioService.upsertDocumentPortfolio(portfolioModel);
        if (saved != null && saved.getOwner() != null) {
            String portfolioId = saved.getId() != null ? saved.getId().toString() : null;
            portfolioHoldingsRedisService.evictPortfolioHoldings(saved.getOwner(), portfolioId);
            portfolioSummaryRedisService.evictPortfolioSummary(saved.getOwner(), portfolioId);
        }
        publishUpdate(saved, event.getSource(), event.getPortfolioId());
    }

    private void processTradeMessage(com.portfolio.model.events.trade.TradePortfolioSyncEvent event) {
        PortfolioModelV1 portfolioModel = portfolioMapper.toPortfolioModelV1(event);
        PortfolioModelV1 saved = portfolioService.updateTradePortfolio(portfolioModel);
        if (saved != null && saved.getOwner() != null) {
            String portfolioId = saved.getId() != null ? saved.getId().toString() : null;
            portfolioHoldingsRedisService.evictPortfolioHoldings(saved.getOwner(), portfolioId);
            portfolioSummaryRedisService.evictPortfolioSummary(saved.getOwner(), portfolioId);
        }
        publishUpdate(saved, "TRADE", event.getId());
    }

    private void publishUpdate(PortfolioModelV1 saved, String source, String originalId) {
        if (saved != null) {
            portfolioEventPublisher.publishPortfolioUpdate(saved, source);
        } else {
            log.warn("[Consumer] Portfolio save returned null for source='{}', portfolioId='{}'. " +
                    "Event will NOT be published downstream.", source, originalId);
        }
    }

    // ── De-duplication helpers ────────────────────────────────────────────────

    /**
     * Builds a unique Redis key for this message.
     *
     * <p>The key encodes: consumer-group + source + portfolioId + partition + offset.
     * Using the Kafka offset makes the key unique per physical message delivery position,
     * which is the correct granularity for at-least-once deduplication.
     */
    private String buildDedupKey(String source, String portfolioId, long offset, int partition) {
        return DEDUP_KEY_PREFIX + consumerGroupId + ":" + source + ":" + portfolioId
                + ":p" + partition + ":o" + offset;
    }

    /**
     * Returns {@code true} if this message has already been successfully processed.
     * Fails open (returns {@code false}) if Redis is unavailable to avoid blocking progress.
     */
    private boolean isDuplicate(String dedupKey) {
        try {
            Boolean exists = stringRedisTemplate.hasKey(dedupKey);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.warn("[DEDUP] Redis check failed for key={} — treating as non-duplicate to avoid blocking: {}", dedupKey, e.getMessage());
            return false; // Fail open: process the message rather than stalling
        }
    }

    /**
     * Marks a message as successfully processed in Redis with a TTL.
     * Fails silently if Redis is unavailable — the worst case is a single re-process on restart.
     */
    private void markProcessed(String dedupKey) {
        try {
            stringRedisTemplate.opsForValue().set(dedupKey, "1", Duration.ofHours(DEDUP_TTL_HOURS));
            log.debug("[DEDUP] Marked as processed: {}", dedupKey);
        } catch (Exception e) {
            log.warn("[DEDUP] Redis write failed for key={}: {}", dedupKey, e.getMessage());
        }
    }
}
