package com.portfolio.kafka.consumer;

import com.am.common.amcommondata.service.PortfolioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.portfolio.kafka.publisher.PortfolioEventPublisher;
import com.portfolio.model.mapper.PortfolioMapperv1;
import com.portfolio.redis.service.ActiveMarketSymbolPublisher;
import com.portfolio.redis.service.PortfolioHoldingsRedisService;
import com.portfolio.redis.service.PortfolioIntelligenceRedisService;
import com.portfolio.redis.service.PortfolioSummaryRedisService;
import com.portfolio.service.resolver.PortfolioEquitySymbolNormalizer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioUpdateConsumerDeleteTest {

    @Mock private PortfolioService portfolioService;
    @Mock private PortfolioEventPublisher portfolioEventPublisher;
    @Mock private PortfolioHoldingsRedisService holdingsRedisService;
    @Mock private PortfolioSummaryRedisService summaryRedisService;
    @Mock private ActiveMarketSymbolPublisher activeMarketSymbolPublisher;
    @Mock private PortfolioIntelligenceRedisService intelligenceRedisService;
    @Mock private PortfolioEquitySymbolNormalizer equitySymbolNormalizer;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private Acknowledgment acknowledgment;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private PortfolioUpdateConsumerService newConsumer() {
        PortfolioUpdateConsumerService c = new PortfolioUpdateConsumerService(
                objectMapper,
                new PortfolioMapperv1(Optional.empty()),
                portfolioService,
                portfolioEventPublisher,
                holdingsRedisService,
                summaryRedisService,
                activeMarketSymbolPublisher,
                intelligenceRedisService,
                equitySymbolNormalizer,
                stringRedisTemplate);
        c.init();
        return c;
    }

    @Test
    void tradeSyncDelete_withUuid_deletesMongoEvictsAndPublishesOutboundDelete() throws Exception {
        UUID portfolioUuid = UUID.randomUUID();
        String owner = "owner-" + UUID.randomUUID();
        String name = "Book-" + UUID.randomUUID().toString().substring(0, 8);
        String eventId = UUID.randomUUID().toString();

        String json = """
                {
                  "eventId": "%s",
                  "eventType": "TRADE_SYNC",
                  "source": "am-trade-management",
                  "dataVersion": "1.0",
                  "id": "%s",
                  "portfolioId": "%s",
                  "userId": "%s",
                  "action": "DELETE_PORTFOLIO",
                  "deleteAllTrades": true,
                  "equities": []
                }
                """.formatted(eventId, portfolioUuid, name, owner);

        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("am-portfolio", 0, 42L, portfolioUuid.toString(), json);

        newConsumer().consume(record, acknowledgment);

        verify(portfolioService).deletePortfolioByIdAndOwner(portfolioUuid.toString(), owner);
        verify(holdingsRedisService).evictPortfolioHoldings(owner, portfolioUuid.toString());
        verify(summaryRedisService).evictPortfolioSummary(owner, portfolioUuid.toString());
        verify(holdingsRedisService).evictPortfolioHoldings(owner, name);
        verify(intelligenceRedisService).evict(portfolioUuid.toString());
        verify(portfolioEventPublisher).publishPortfolioDelete(
                eq(owner), eq(portfolioUuid.toString()), eq(name), eq("am-trade-management"));
        verify(acknowledgment).acknowledge();
        verify(equitySymbolNormalizer, never()).normalizePortfolio(any());
    }

    @Test
    void tradeSyncDelete_blankOwner_skipsDeleteAndPublish() throws Exception {
        UUID portfolioUuid = UUID.randomUUID();
        String json = """
                {
                  "eventId": "%s",
                  "eventType": "TRADE_SYNC",
                  "id": "%s",
                  "portfolioId": "SomeName",
                  "userId": "",
                  "action": "DELETE_PORTFOLIO",
                  "deleteAllTrades": true,
                  "equities": []
                }
                """.formatted(UUID.randomUUID(), portfolioUuid);

        newConsumer().consume(
                new ConsumerRecord<>("am-portfolio", 0, 1L, "k", json), acknowledgment);

        verify(portfolioService, never()).deletePortfolioByIdAndOwner(anyString(), anyString());
        verify(portfolioEventPublisher, never()).publishPortfolioDelete(any(), any(), any(), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void tradeSyncDelete_nameOnly_deletesByName_skipsOutboundPublish() throws Exception {
        String owner = "owner-" + UUID.randomUUID();
        String name = "LegacyName-" + UUID.randomUUID().toString().substring(0, 8);
        String json = """
                {
                  "eventId": "%s",
                  "eventType": "TRADE_SYNC",
                  "source": "am-trade-management",
                  "id": "",
                  "portfolioId": "%s",
                  "userId": "%s",
                  "action": "DELETE_PORTFOLIO",
                  "deleteAllTrades": true,
                  "equities": []
                }
                """.formatted(UUID.randomUUID(), name, owner);

        newConsumer().consume(
                new ConsumerRecord<>("am-portfolio", 0, 2L, name, json), acknowledgment);

        verify(portfolioService).deletePortfolioByIdAndOwner(name, owner);
        verify(holdingsRedisService).evictPortfolioHoldings(owner, name);
        verify(summaryRedisService).evictPortfolioSummary(owner, name);
        verify(intelligenceRedisService, never()).evict(anyString());
        verify(portfolioEventPublisher, never()).publishPortfolioDelete(any(), any(), any(), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void tradeSyncDelete_invalidId_fallsBackToName_skipsOutbound() throws Exception {
        String owner = "owner-" + UUID.randomUUID();
        String name = "Book-" + UUID.randomUUID().toString().substring(0, 8);
        String json = """
                {
                  "eventId": "%s",
                  "eventType": "TRADE_SYNC",
                  "source": "am-trade-management",
                  "id": "not-a-uuid",
                  "portfolioId": "%s",
                  "userId": "%s",
                  "action": "DELETE_PORTFOLIO",
                  "deleteAllTrades": true,
                  "equities": []
                }
                """.formatted(UUID.randomUUID(), name, owner);

        newConsumer().consume(
                new ConsumerRecord<>("am-portfolio", 0, 3L, "k", json), acknowledgment);

        verify(portfolioService).deletePortfolioByIdAndOwner(name, owner);
        verify(portfolioEventPublisher, never()).publishPortfolioDelete(any(), any(), any(), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void tradeSyncDelete_nameEqualsUuid_evictsOnceAndPublishes() throws Exception {
        UUID portfolioUuid = UUID.randomUUID();
        String owner = "owner-" + UUID.randomUUID();
        String id = portfolioUuid.toString();
        String json = """
                {
                  "eventId": "%s",
                  "eventType": "TRADE_SYNC",
                  "source": "am-trade-management",
                  "id": "%s",
                  "portfolioId": "%s",
                  "userId": "%s",
                  "action": "DELETE_PORTFOLIO",
                  "deleteAllTrades": true,
                  "equities": []
                }
                """.formatted(UUID.randomUUID(), id, id, owner);

        newConsumer().consume(
                new ConsumerRecord<>("am-portfolio", 0, 4L, id, json), acknowledgment);

        verify(portfolioService).deletePortfolioByIdAndOwner(id, owner);
        org.mockito.Mockito.verify(holdingsRedisService, org.mockito.Mockito.times(1))
                .evictPortfolioHoldings(eq(owner), eq(id));
        verify(portfolioEventPublisher).publishPortfolioDelete(
                eq(owner), eq(id), eq(id), eq("am-trade-management"));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void tradeSyncDelete_missingIdAndName_skips() throws Exception {
        String owner = "owner-" + UUID.randomUUID();
        String json = """
                {
                  "eventId": "%s",
                  "eventType": "TRADE_SYNC",
                  "source": "am-trade-management",
                  "id": "",
                  "portfolioId": "",
                  "userId": "%s",
                  "action": "DELETE_PORTFOLIO",
                  "deleteAllTrades": true,
                  "equities": []
                }
                """.formatted(UUID.randomUUID(), owner);

        newConsumer().consume(
                new ConsumerRecord<>("am-portfolio", 0, 5L, "k", json), acknowledgment);

        verify(portfolioService, never()).deletePortfolioByIdAndOwner(anyString(), anyString());
        verify(portfolioEventPublisher, never()).publishPortfolioDelete(any(), any(), any(), any());
        verify(acknowledgment).acknowledge();
    }
}
